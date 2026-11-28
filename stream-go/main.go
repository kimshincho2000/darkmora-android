package main

import (
	"bufio"
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	_ "github.com/go-sql-driver/mysql"
	"github.com/gotd/td/session"
	"github.com/gotd/td/telegram"
	"github.com/gotd/td/tg"
)

const (
	internalStreamSecret = "darkmora_turbo_internal_secret_2026"
	chunkSize            = 1024 * 1024 // 1MB sustained
	chunkAlign           = 1024 * 1024 // 1MB singleflight/SHM alignment
	seekFirstChunk       = 256 * 1024  // 256KB instant seek first burst
	headCacheSize        = 4 * 1024 * 1024
	tailCacheSize        = 2 * 1024 * 1024
	maxRangeWindow       = 8 * 1024 * 1024
	seekRangeWindow      = 6 * 1024 * 1024
	msgCacheTTL          = 6 * time.Hour
	chunkFetchTimeout    = 10 * time.Second
)

var (
	baseDir         string
	adminDir        string
	stateDir        string
	goSessionsDir   string
	shmDir          = "/dev/shm/anirate_stream"
	transcodedDir   string
	apiID           int
	apiHash         string
	streamSecret    string
	dbDSN           string
	listenAddr      = "127.0.0.1:8081"
	startTime       = time.Now()
	rangeRegex      = regexp.MustCompile(`bytes=(\d*)-(\d*)`)
	safeNameRegex   = regexp.MustCompile(`[^\w\.-]`)

	dbPool *sql.DB

	storageChanMu   sync.RWMutex
	storageChanID   int64 = -1004291159204
	storageChanAt   time.Time

	tokenCacheMu    sync.RWMutex
	tokenCache      = make(map[string]time.Time)

	epLookupMu      sync.RWMutex
	epLookupCache   = make(map[[2]int]epLookupEntry)

	metaMu          sync.RWMutex
	metaCache       = make(map[int]mediaMeta)

	inflightMu      sync.Mutex
	inflightChunks  = make(map[[2]int64]*inflightCall)

	statRequests     atomic.Int64
	statBytes        atomic.Int64
	statErrors       atomic.Int64
	statHitHead      atomic.Int64
	statHitTail      atomic.Int64
	statHitShm       atomic.Int64
	statHitSF        atomic.Int64
	statHitSSD       atomic.Int64
	statFailovers    atomic.Int64
)

type epLookupEntry struct {
	msgID    int
	videoURL string
	ts       time.Time
}

type mediaMeta struct {
	size int64
	mime string
	ts   time.Time
}

type inflightCall struct {
	done chan struct{}
	data []byte
	err  error
}

type cachedDoc struct {
	doc *tg.Document
	ts  time.Time
}

type BotWorker struct {
	idx           int
	token         string
	botID         string
	username      string
	client        *telegram.Client
	api           *tg.Client
	homeDC        int

	ready         atomic.Bool
	disabled      atomic.Bool
	activeReqs    atomic.Int64
	totalReqs     atomic.Int64
	totalBytes    atomic.Int64
	errorCount    atomic.Int64
	avgChunkNs    atomic.Int64
	cooldownUntil atomic.Int64

	chanMu        sync.RWMutex
	inputChannel  *tg.InputChannel

	docMu         sync.RWMutex
	docCache      map[int]cachedDoc

	dcMu          sync.Mutex
	dcClients     map[int]*tg.Client
	dcClosers     map[int]func()
}

func newBotWorker(token string, idx int) *BotWorker {
	botID := strings.SplitN(token, ":", 2)[0]
	w := &BotWorker{
		idx:       idx,
		token:     token,
		botID:     botID,
		docCache:  make(map[int]cachedDoc),
		dcClients: make(map[int]*tg.Client),
		dcClosers: make(map[int]func()),
	}
	w.avgChunkNs.Store(int64(150 * time.Millisecond))
	return w
}

func (w *BotWorker) recordLatency(d time.Duration) {
	old := w.avgChunkNs.Load()
	next := (old*8 + int64(d)*2) / 10
	w.avgChunkNs.Store(next)
}

func (w *BotWorker) run(ctx context.Context, readyCh chan<- bool) {
	sessPath := filepath.Join(goSessionsDir, fmt.Sprintf("bot_%s.json", w.botID))
	client := telegram.NewClient(apiID, apiHash, telegram.Options{
		SessionStorage: &session.FileStorage{Path: sessPath},
		RetryInterval:  2 * time.Second,
		MaxRetries:     5,
	})
	w.client = client

	signaled := false
	signalOnce := func(ok bool) {
		if !signaled {
			signaled = true
			readyCh <- ok
		}
	}

	err := client.Run(ctx, func(ctx context.Context) error {
		st, err := client.Auth().Status(ctx)
		if err != nil {
			signalOnce(false)
			return err
		}
		if !st.Authorized {
			if _, err := client.Auth().Bot(ctx, w.token); err != nil {
				w.disabled.Store(true)
				signalOnce(false)
				return err
			}
		}
		self, err := client.Self(ctx)
		if err != nil {
			signalOnce(false)
			return err
		}
		w.username = self.Username
		w.api = client.API()
		if cfg, err := w.api.HelpGetConfig(ctx); err == nil {
			w.homeDC = cfg.ThisDC
		}
		w.ready.Store(true)
		log.Printf("Go BotWorker #%d ready: @%s (ID: %s, DC: %d)", w.idx, w.username, w.botID, w.homeDC)
		signalOnce(true)
		<-ctx.Done()
		w.dcMu.Lock()
		for _, closer := range w.dcClosers {
			closer()
		}
		w.dcMu.Unlock()
		return ctx.Err()
	})
	if err != nil && !errors.Is(err, context.Canceled) {
		log.Printf("BotWorker #%d (%s) stopped: %v", w.idx, w.botID, err)
	}
	w.ready.Store(false)
	signalOnce(false)
}

func rawChannelID(cid int64) int64 {
	s := strconv.FormatInt(cid, 10)
	s = strings.TrimPrefix(s, "-100")
	s = strings.TrimPrefix(s, "-")
	v, _ := strconv.ParseInt(s, 10, 64)
	return v
}

func (w *BotWorker) resolveChannel(ctx context.Context, channelID int64) (*tg.InputChannel, error) {
	w.chanMu.RLock()
	if w.inputChannel != nil {
		ic := w.inputChannel
		w.chanMu.RUnlock()
		return ic, nil
	}
	w.chanMu.RUnlock()

	rawID := rawChannelID(channelID)
	res, err := w.api.ChannelsGetChannels(ctx, []tg.InputChannelClass{
		&tg.InputChannel{ChannelID: rawID, AccessHash: 0},
	})
	if err != nil {
		return nil, err
	}
	var chats []tg.ChatClass
	switch v := res.(type) {
	case *tg.MessagesChats:
		chats = v.Chats
	case *tg.MessagesChatsSlice:
		chats = v.Chats
	}
	for _, c := range chats {
		if ch, ok := c.(*tg.Channel); ok && ch.ID == rawID {
			ic := &tg.InputChannel{ChannelID: ch.ID, AccessHash: ch.AccessHash}
			w.chanMu.Lock()
			w.inputChannel = ic
			w.chanMu.Unlock()
			return ic, nil
		}
	}
	return &tg.InputChannel{ChannelID: rawID, AccessHash: 0}, nil
}

func (w *BotWorker) getDoc(ctx context.Context, channelID int64, msgID int, forceRefresh bool) (*tg.Document, error) {
	if !forceRefresh {
		w.docMu.RLock()
		if cd, ok := w.docCache[msgID]; ok && time.Since(cd.ts) < msgCacheTTL {
			w.docMu.RUnlock()
			return cd.doc, nil
		}
		w.docMu.RUnlock()
	}

	cctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	ic, err := w.resolveChannel(cctx, channelID)
	if err != nil {
		return nil, err
	}
	res, err := w.api.ChannelsGetMessages(cctx, &tg.ChannelsGetMessagesRequest{
		Channel: ic,
		ID:      []tg.InputMessageClass{&tg.InputMessageID{ID: msgID}},
	})
	if err != nil {
		return nil, err
	}
	var msgs []tg.MessageClass
	switch v := res.(type) {
	case *tg.MessagesChannelMessages:
		msgs = v.Messages
	case *tg.MessagesMessages:
		msgs = v.Messages
	case *tg.MessagesMessagesSlice:
		msgs = v.Messages
	}
	for _, m := range msgs {
		msg, ok := m.(*tg.Message)
		if !ok || msg.Media == nil {
			continue
		}
		md, ok := msg.Media.(*tg.MessageMediaDocument)
		if !ok || md.Document == nil {
			continue
		}
		doc, ok := md.Document.(*tg.Document)
		if !ok {
			continue
		}
		w.docMu.Lock()
		if len(w.docCache) > 300 {
			w.docCache = make(map[int]cachedDoc)
		}
		w.docCache[msgID] = cachedDoc{doc: doc, ts: time.Now()}
		w.docMu.Unlock()
		return doc, nil
	}
	return nil, errors.New("document not found")
}

func (w *BotWorker) getDCAPI(ctx context.Context, dcID int) (*tg.Client, error) {
	if dcID == 0 || dcID == w.homeDC {
		return w.api, nil
	}
	w.dcMu.Lock()
	defer w.dcMu.Unlock()
	if c, ok := w.dcClients[dcID]; ok {
		return c, nil
	}
	inv, err := w.client.DC(ctx, dcID, 1)
	if err != nil {
		return nil, err
	}
	c := tg.NewClient(inv)
	w.dcClients[dcID] = c
	w.dcClosers[dcID] = func() { _ = inv.Close() }
	return c, nil
}

func (w *BotWorker) fetchChunk(ctx context.Context, doc *tg.Document, offset int64, limit int) ([]byte, error) {
	cctx, cancel := context.WithTimeout(ctx, chunkFetchTimeout)
	defer cancel()

	api, err := w.getDCAPI(cctx, doc.DCID)
	if err != nil {
		return nil, err
	}
	t0 := time.Now()
	res, err := api.UploadGetFile(cctx, &tg.UploadGetFileRequest{
		Precise: true,
		Location: &tg.InputDocumentFileLocation{
			ID:            doc.ID,
			AccessHash:    doc.AccessHash,
			FileReference: doc.FileReference,
		},
		Offset: offset,
		Limit:  limit,
	})
	if err != nil {
		return nil, err
	}
	w.recordLatency(time.Since(t0))
	switch v := res.(type) {
	case *tg.UploadFile:
		return v.Bytes, nil
	default:
		return nil, errors.New("unexpected upload file response")
	}
}

type BotPool struct {
	mu      sync.RWMutex
	workers []*BotWorker
	rr      atomic.Uint64
}

var pool = &BotPool{}

func (p *BotPool) init(ctx context.Context, tokens []string) {
	readyCh := make(chan bool, len(tokens))
	workers := make([]*BotWorker, 0, len(tokens))
	for i, tok := range tokens {
		w := newBotWorker(tok, i)
		workers = append(workers, w)
		go w.run(ctx, readyCh)
	}
	online := 0
	deadline := time.After(25 * time.Second)
	for i := 0; i < len(tokens); i++ {
		select {
		case ok := <-readyCh:
			if ok {
				online++
			}
		case <-deadline:
			i = len(tokens)
		}
	}
	p.mu.Lock()
	for _, w := range workers {
		if w.ready.Load() {
			p.workers = append(p.workers, w)
		}
	}
	p.mu.Unlock()
	log.Printf("Go BotPool active: %d / %d online workers", online, len(tokens))
}

func (p *BotPool) getBestWorker(excluded map[*BotWorker]bool, preferMsgID int) *BotWorker {
	p.mu.RLock()
	defer p.mu.RUnlock()
	now := time.Now().Unix()

	var ready []*BotWorker
	var fallback []*BotWorker
	for _, w := range p.workers {
		if !w.ready.Load() || w.disabled.Load() || excluded[w] {
			continue
		}
		fallback = append(fallback, w)
		if w.cooldownUntil.Load() <= now {
			ready = append(ready, w)
		}
	}
	if len(ready) == 0 {
		ready = fallback
	}
	if len(ready) == 0 {
		return nil
	}

	minActive := ready[0].activeReqs.Load()
	for _, w := range ready[1:] {
		if a := w.activeReqs.Load(); a < minActive {
			minActive = a
		}
	}

	// Fast-Seek Doc-Cache Affinity
	if preferMsgID > 0 {
		var cached []*BotWorker
		for _, w := range ready {
			if w.activeReqs.Load() <= minActive+1 {
				w.docMu.RLock()
				cd, ok := w.docCache[preferMsgID]
				w.docMu.RUnlock()
				if ok && time.Since(cd.ts) < msgCacheTTL {
					cached = append(cached, w)
				}
			}
		}
		if len(cached) > 0 {
			sort.Slice(cached, func(i, j int) bool {
				ai, aj := cached[i].activeReqs.Load(), cached[j].activeReqs.Load()
				if ai != aj {
					return ai < aj
				}
				return cached[i].avgChunkNs.Load() < cached[j].avgChunkNs.Load()
			})
			return cached[0]
		}
	}

	var least []*BotWorker
	for _, w := range ready {
		if w.activeReqs.Load() == minActive {
			least = append(least, w)
		}
	}
	idx := p.rr.Add(1) % uint64(len(least))
	return least[idx]
}

// ─── CONFIG & DB HELPERS ─────────────────────────────────────────────────────

func loadEnv(path string) {
	f, err := os.Open(path)
	if err != nil {
		return
	}
	defer f.Close()
	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") || !strings.Contains(line, "=") {
			continue
		}
		parts := strings.SplitN(line, "=", 2)
		k := strings.TrimSpace(parts[0])
		v := strings.Trim(strings.TrimSpace(parts[1]), `"'`)
		if k != "" && os.Getenv(k) == "" {
			_ = os.Setenv(k, v)
		}
	}
}

func getStorageChannelID() int64 {
	storageChanMu.RLock()
	if time.Since(storageChanAt) < 60*time.Second {
		id := storageChanID
		storageChanMu.RUnlock()
		return id
	}
	storageChanMu.RUnlock()

	id := int64(-1004291159204)
	if b, err := os.ReadFile(filepath.Join(adminDir, "storage_kanal.txt")); err == nil {
		if v, err := strconv.ParseInt(strings.TrimSpace(string(b)), 10, 64); err == nil && v != 0 {
			id = v
		}
	}
	storageChanMu.Lock()
	storageChanID = id
	storageChanAt = time.Now()
	storageChanMu.Unlock()
	return id
}

func loadBotTokens() []string {
	seen := make(map[string]bool)
	var out []string
	add := func(t string) {
		t = strings.TrimSpace(t)
		if t != "" && strings.Contains(t, ":") && !seen[t] {
			seen[t] = true
			out = append(out, t)
		}
	}
	if b, err := os.ReadFile(filepath.Join(adminDir, "stream_bots.txt")); err == nil {
		for _, line := range strings.Split(string(b), "\n") {
			add(line)
		}
	}
	if dbPool != nil {
		rows, err := dbPool.Query("SELECT token FROM bot_clones WHERE status = 'active' AND token IS NOT NULL")
		if err == nil {
			defer rows.Close()
			for rows.Next() {
				var tok string
				if rows.Scan(&tok) == nil {
					add(tok)
				}
			}
		}
	}
	return out
}

func clientSubnet(ip string) string {
	if strings.Contains(ip, ":") {
		parts := strings.Split(ip, ":")
		if len(parts) >= 3 {
			return strings.Join(parts[:3], ":")
		}
		return ip
	}
	parts := strings.Split(ip, ".")
	if len(parts) >= 3 {
		return parts[0] + "." + parts[1] + "." + parts[2]
	}
	return "127.0.0"
}

func hmacHex(secret, msg string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(msg))
	return hex.EncodeToString(mac.Sum(nil))
}

func verifyStreamToken(token string, animeID, qism, msgID int, ip string) bool {
	if token == "" {
		return false
	}
	if token == internalStreamSecret {
		return true
	}
	subnet := clientSubnet(ip)
	ck := fmt.Sprintf("%s:%d:%d:%d:%s", token, animeID, qism, msgID, subnet)
	tokenCacheMu.RLock()
	if exp, ok := tokenCache[ck]; ok && time.Now().Before(exp) {
		tokenCacheMu.RUnlock()
		return true
	}
	tokenCacheMu.RUnlock()

	now := time.Now().UTC()
	slots := make(map[string]bool)
	for h := -6; h <= 6; h++ {
		t := now.Add(time.Duration(h) * time.Hour)
		slots[t.Add(5*time.Hour).Format("2006010215")] = true
		slots[t.Format("2006010215")] = true
	}

	valid := false
	for slot := range slots {
		if msgID > 0 {
			if hmac.Equal([]byte(hmacHex(streamSecret, fmt.Sprintf("stream:msg:%d:%s", msgID, slot))), []byte(token)) {
				valid = true
				break
			}
		}
		if animeID > 0 && qism > 0 {
			if hmac.Equal([]byte(hmacHex(streamSecret, fmt.Sprintf("stream:%d:%d:%s", animeID, qism, slot))), []byte(token)) ||
				hmac.Equal([]byte(hmacHex(streamSecret, fmt.Sprintf("stream:%d:%d:%s:%s", animeID, qism, subnet, slot))), []byte(token)) {
				valid = true
				break
			}
		}
	}
	if valid {
		tokenCacheMu.Lock()
		if len(tokenCache) > 2000 {
			tokenCache = make(map[string]time.Time)
		}
		tokenCache[ck] = time.Now().Add(5 * time.Minute)
		tokenCacheMu.Unlock()
	}
	return valid
}

func resolveEpisode(animeID, qism int) (int, string) {
	key := [2]int{animeID, qism}
	epLookupMu.RLock()
	if e, ok := epLookupCache[key]; ok && time.Since(e.ts) < time.Hour {
		epLookupMu.RUnlock()
		return e.msgID, e.videoURL
	}
	epLookupMu.RUnlock()

	// Check local ep_meta json first
	metaPath := filepath.Join(stateDir, fmt.Sprintf("ep_meta_%d_%d.json", animeID, qism))
	if b, err := os.ReadFile(metaPath); err == nil {
		var m map[string]any
		if json.Unmarshal(b, &m) == nil {
			if f, ok := m["storage_msg_id"].(float64); ok && int(f) > 0 {
				mid := int(f)
				epLookupMu.Lock()
				epLookupCache[key] = epLookupEntry{msgID: mid, ts: time.Now()}
				epLookupMu.Unlock()
				return mid, ""
			}
		}
	}

	if dbPool != nil {
		var mid sql.NullInt64
		var vurl sql.NullString
		err := dbPool.QueryRow("SELECT storage_msg_id, video_url FROM anime_datas WHERE id = ? AND qism = ? LIMIT 1", animeID, qism).Scan(&mid, &vurl)
		if err == nil {
			mVal := int(mid.Int64)
			uVal := strings.TrimSpace(vurl.String)
			if mVal > 0 || uVal != "" {
				epLookupMu.Lock()
				if len(epLookupCache) > 2000 {
					epLookupCache = make(map[[2]int]epLookupEntry)
				}
				epLookupCache[key] = epLookupEntry{msgID: mVal, videoURL: uVal, ts: time.Now()}
				epLookupMu.Unlock()
				return mVal, uVal
			}
		}
	}
	return 0, ""
}

func getMediaMeta(ctx context.Context, msgID int) (int64, string, error) {
	metaMu.RLock()
	if m, ok := metaCache[msgID]; ok && time.Since(m.ts) < msgCacheTTL {
		metaMu.RUnlock()
		return m.size, m.mime, nil
	}
	metaMu.RUnlock()

	cid := getStorageChannelID()
	excluded := make(map[*BotWorker]bool)
	for i := 0; i < 4; i++ {
		w := pool.getBestWorker(excluded, msgID)
		if w == nil {
			break
		}
		doc, err := w.getDoc(ctx, cid, msgID, false)
		if err == nil && doc != nil {
			mime := doc.MimeType
			if mime == "" {
				mime = "video/mp4"
			}
			metaMu.Lock()
			metaCache[msgID] = mediaMeta{size: doc.Size, mime: mime, ts: time.Now()}
			metaMu.Unlock()
			return doc.Size, mime, nil
		}
		excluded[w] = true
	}
	return 0, "", errors.New("media metadata not found")
}

func parseRange(hdr string, total int64, isDownload bool) (int64, int64) {
	if hdr == "" {
		return 0, total - 1
	}
	m := rangeRegex.FindStringSubmatch(strings.TrimSpace(hdr))
	if len(m) != 3 {
		return 0, total - 1
	}
	var start, end int64
	if m[1] != "" && m[2] != "" {
		start, _ = strconv.ParseInt(m[1], 10, 64)
		end, _ = strconv.ParseInt(m[2], 10, 64)
	} else if m[1] != "" {
		start, _ = strconv.ParseInt(m[1], 10, 64)
		if isDownload {
			end = total - 1
		} else {
			win := int64(seekRangeWindow)
			if start == 0 {
				win = maxRangeWindow
			}
			if total-start <= win+tailCacheSize {
				end = total - 1
			} else {
				end = min(total-1, start+win-1)
			}
		}
	} else if m[2] != "" {
		suf, _ := strconv.ParseInt(m[2], 10, 64)
		start = max(0, total-suf)
		end = total - 1
	} else {
		return 0, total - 1
	}
	if start < 0 {
		start = 0
	}
	if start >= total {
		start = total - 1
	}
	if end < start {
		end = start
	}
	if end >= total {
		end = total - 1
	}
	return start, end
}

func writeFileAtomic(path string, data []byte) {
	tmp := path + ".tmp"
	if os.WriteFile(tmp, data, 0644) == nil {
		_ = os.Rename(tmp, path)
	}
}

func findSSDCached(msgID int) string {
	for _, pfx := range []string{"", "hot_"} {
		p := filepath.Join(transcodedDir, fmt.Sprintf("%s%d.mp4", pfx, msgID))
		if fi, err := os.Stat(p); err == nil && fi.Size() > 1_000_000 {
			return p
		}
	}
	return ""
}

// Singleflight 1MB block fetcher (or fast 256KB seek burst without SHM pollution)
func fetchBlockSingleflight(ctx context.Context, msgID int, alignedOffset int64, limit int, cacheSHM bool) ([]byte, error) {
	blkPath := filepath.Join(shmDir, fmt.Sprintf("blk_%d_%d.bin", msgID, alignedOffset))
	if limit == chunkSize && cacheSHM {
		if b, err := os.ReadFile(blkPath); err == nil && len(b) > 0 {
			statHitShm.Add(1)
			return b, nil
		}
	}

	key := [2]int64{int64(msgID), alignedOffset*10 + int64(limit/seekFirstChunk)}
	inflightMu.Lock()
	if call, ok := inflightChunks[key]; ok {
		inflightMu.Unlock()
		statHitSF.Add(1)
		select {
		case <-call.done:
			return call.data, call.err
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
	call := &inflightCall{done: make(chan struct{})}
	inflightChunks[key] = call
	inflightMu.Unlock()

	defer func() {
		close(call.done)
		inflightMu.Lock()
		delete(inflightChunks, key)
		inflightMu.Unlock()
	}()

	cid := getStorageChannelID()
	excluded := make(map[*BotWorker]bool)
	var lastErr error

	for attempt := 0; attempt < 4; attempt++ {
		w := pool.getBestWorker(excluded, msgID)
		if w == nil {
			break
		}
		doc, err := w.getDoc(ctx, cid, msgID, attempt > 0)
		if err != nil || doc == nil {
			excluded[w] = true
			lastErr = err
			continue
		}
		w.activeReqs.Add(1)
		w.totalReqs.Add(1)
		data, err := w.fetchChunk(ctx, doc, alignedOffset, limit)
		w.activeReqs.Add(-1)
		if err == nil && len(data) > 0 {
			w.totalBytes.Add(int64(len(data)))
			if cacheSHM && limit == chunkSize && len(data) == chunkSize && alignedOffset%chunkAlign == 0 {
				cp := make([]byte, len(data))
				copy(cp, data)
				go writeFileAtomic(blkPath, cp)
			}
			call.data = data
			return data, nil
		}
		w.errorCount.Add(1)
		statFailovers.Add(1)
		w.cooldownUntil.Store(time.Now().Add(2 * time.Second).Unix())
		excluded[w] = true
		lastErr = err
	}
	if lastErr == nil {
		lastErr = errors.New("all workers failed")
	}
	call.err = lastErr
	return nil, lastErr
}

func handleStream(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodOptions {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Range, Content-Type")
		w.Header().Set("Access-Control-Max-Age", "86400")
		w.WriteHeader(http.StatusNoContent)
		return
	}

	statRequests.Add(1)
	q := r.URL.Query()
	msgID := 0
	if strings.HasPrefix(r.URL.Path, "/stream/") {
		seg := strings.TrimPrefix(r.URL.Path, "/stream/")
		if v, err := strconv.Atoi(seg); err == nil && v > 0 {
			msgID = v
		}
	}
	if msgID <= 0 {
		msgID, _ = strconv.Atoi(q.Get("msg_id"))
	}
	animeID, _ := strconv.Atoi(q.Get("id"))
	qism, _ := strconv.Atoi(q.Get("qism"))
	token := q.Get("token")
	isDownload := q.Get("download") == "1"
	name := q.Get("name")
	if name == "" {
		name = fmt.Sprintf("DarkMora_Ep%d.mp4", max(1, qism))
	}

	clientIP := r.Header.Get("X-Real-IP")
	if clientIP == "" {
		if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
			clientIP = strings.TrimSpace(strings.Split(xff, ",")[0])
		}
	}
	if clientIP == "" {
		clientIP = strings.Split(r.RemoteAddr, ":")[0]
	}

	if clientIP != "127.0.0.1" && clientIP != "::1" {
		if !verifyStreamToken(token, animeID, qism, msgID, clientIP) {
			statErrors.Add(1)
			http.Error(w, "403 Forbidden: Token yaroqsiz", http.StatusForbidden)
			return
		}
	}

	extURL := ""
	if msgID <= 0 && animeID > 0 && qism > 0 {
		msgID, extURL = resolveEpisode(animeID, qism)
	}
	if msgID <= 0 {
		if extURL != "" {
			http.Redirect(w, r, extURL, http.StatusFound)
			return
		}
		http.Error(w, "Video topilmadi", http.StatusNotFound)
		return
	}

	// 0. Direct SSD Cached File
	if ssd := findSSDCached(msgID); ssd != "" {
		statHitSSD.Add(1)
		if !isDownload {
			http.Redirect(w, r, "/uploads/transcoded/"+filepath.Base(ssd), http.StatusFound)
			return
		}
		http.ServeFile(w, r, ssd)
		return
	}

	totalSize, mimeType, err := getMediaMeta(r.Context(), msgID)
	if err != nil || totalSize <= 0 {
		statErrors.Add(1)
		http.Error(w, "Video fayl topilmadi", http.StatusNotFound)
		return
	}

	rangeHdr := r.Header.Get("Range")
	hasRange := rangeHdr != ""
	start, end := parseRange(rangeHdr, totalSize, isDownload)
	contentLen := end - start + 1

	ct := mimeType
	if !strings.HasPrefix(ct, "video/") {
		ct = "video/mp4"
	}
	w.Header().Set("Content-Type", ct)
	w.Header().Set("Accept-Ranges", "bytes")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Headers", "Range")
	w.Header().Set("X-Accel-Buffering", "no")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.Header().Set("X-Stream-Engine", "DarkMora-Go-v1.0")

	if isDownload {
		safe := safeNameRegex.ReplaceAllString(name, "_")
		if !strings.HasSuffix(strings.ToLower(safe), ".mp4") {
			safe += ".mp4"
		}
		w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="%s"`, safe))
	}

	if hasRange {
		w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", start, end, totalSize))
		w.Header().Set("Content-Length", strconv.FormatInt(contentLen, 10))
		w.WriteHeader(http.StatusPartialContent)
	} else {
		w.Header().Set("Content-Length", strconv.FormatInt(totalSize, 10))
		w.WriteHeader(http.StatusOK)
	}

	if r.Method == http.MethodHead {
		return
	}

	flusher, _ := w.(http.Flusher)
	pos := start
	needed := contentLen

	// 1. SHM HEAD cache
	headPath := filepath.Join(shmDir, fmt.Sprintf("head_%d.bin", msgID))
	if pos < headCacheSize {
		if f, err := os.Open(headPath); err == nil {
			if fi, err := f.Stat(); err == nil && fi.Size() > pos {
				toRead := min(fi.Size()-pos, needed)
				if _, err := f.Seek(pos, io.SeekStart); err == nil {
					if n, err := io.CopyN(w, f, toRead); err == nil && n > 0 {
						pos += n
						needed -= n
						statHitHead.Add(1)
						statBytes.Add(n)
						if flusher != nil {
							flusher.Flush()
						}
					}
				}
			}
			f.Close()
		}
	}

	// 2. SHM TAIL cache
	tailPath := filepath.Join(shmDir, fmt.Sprintf("tail_%d.bin", msgID))
	if needed > 0 && pos >= max(0, totalSize-tailCacheSize) {
		if f, err := os.Open(tailPath); err == nil {
			if fi, err := f.Stat(); err == nil {
				tailOff := pos - (totalSize - fi.Size())
				if tailOff >= 0 && tailOff < fi.Size() {
					toRead := min(fi.Size()-tailOff, needed)
					if _, err := f.Seek(tailOff, io.SeekStart); err == nil {
						if n, err := io.CopyN(w, f, toRead); err == nil && n > 0 {
							pos += n
							needed -= n
							statHitTail.Add(1)
							statBytes.Add(n)
							if flusher != nil {
								flusher.Flush()
							}
						}
					}
				}
			}
			f.Close()
		}
	}

	if needed <= 0 {
		return
	}

	// 3. Fast 256KB Seek Burst + 1MB Pipelined Singleflight Streaming
	ctx := r.Context()
	for needed > 0 {
		select {
		case <-ctx.Done():
			return
		default:
		}

		// Choose 256KB alignment for mid-file seek when not on a 1MB boundary, else 1MB
		var alignedOff int64
		var reqLimit int
		cacheBlk := !isDownload

		if pos > 0 && !isDownload && (pos%chunkAlign != 0) {
			alignedOff = (pos / seekFirstChunk) * seekFirstChunk
			reqLimit = seekFirstChunk
			cacheBlk = false
		} else {
			alignedOff = (pos / chunkAlign) * chunkAlign
			reqLimit = chunkSize
		}

		data, err := fetchBlockSingleflight(ctx, msgID, alignedOff, reqLimit, cacheBlk)
		if err != nil || len(data) == 0 {
			statErrors.Add(1)
			return
		}

		skip := pos - alignedOff
		if skip < 0 || skip >= int64(len(data)) {
			return
		}
		piece := data[skip:]
		if int64(len(piece)) > needed {
			piece = piece[:needed]
		}
		nw, err := w.Write(piece)
		if err != nil {
			return
		}
		if flusher != nil {
			flusher.Flush()
		}
		pos += int64(nw)
		needed -= int64(nw)
		statBytes.Add(int64(nw))
	}
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	var mem runtime.MemStats
	runtime.ReadMemStats(&mem)

	pool.mu.RLock()
	workers := make([]map[string]any, 0, len(pool.workers))
	online := 0
	active := int64(0)
	for _, bw := range pool.workers {
		if bw.ready.Load() {
			online++
		}
		a := bw.activeReqs.Load()
		active += a
		bw.docMu.RLock()
		dc := len(bw.docCache)
		bw.docMu.RUnlock()
		workers = append(workers, map[string]any{
			"idx":             bw.idx,
			"id":              bw.botID,
			"username":        bw.username,
			"active_requests": a,
			"total_requests":  bw.totalReqs.Load(),
			"total_mb":        float64(bw.totalBytes.Load()) / (1024 * 1024),
			"avg_chunk_ms":    float64(bw.avgChunkNs.Load()) / 1e6,
			"cached_docs":     dc,
			"errors":          bw.errorCount.Load(),
		})
	}
	pool.mu.RUnlock()

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]any{
		"status":         "ok",
		"engine":         "go-gotd",
		"version":        "1.0",
		"uptime":         int(time.Since(startTime).Seconds()),
		"go_heap_mb":     float64(mem.HeapAlloc) / (1024 * 1024),
		"go_sys_mb":      float64(mem.Sys) / (1024 * 1024),
		"goroutines":     runtime.NumGoroutine(),
		"pool": map[string]any{
			"total_bots":      len(workers),
			"online_bots":     online,
			"active_requests": active,
			"workers":         workers,
		},
		"stats": map[string]any{
			"total_requests":       statRequests.Load(),
			"total_bytes_served":   statBytes.Load(),
			"total_errors":         statErrors.Load(),
			"cache_hits_head":      statHitHead.Load(),
			"cache_hits_tail":      statHitTail.Load(),
			"cache_hits_block_shm": statHitShm.Load(),
			"cache_hits_sf":        statHitSF.Load(),
			"cache_hits_ssd":       statHitSSD.Load(),
			"bot_failovers":        statFailovers.Load(),
		},
	})
}

func main() {
	exePath, _ := os.Executable()
	baseDir = os.Getenv("DARKMORA_BASE_DIR")
	if baseDir == "" {
		baseDir = filepath.Dir(filepath.Dir(exePath))
	}
	if _, err := os.Stat(filepath.Join(baseDir, ".env")); err != nil {
		baseDir = "/home/terminal/darkmora.uz"
	}
	loadEnv(filepath.Join(baseDir, ".env"))

	adminDir = filepath.Join(baseDir, "admin")
	stateDir = filepath.Join(baseDir, "state")
	goSessionsDir = filepath.Join(stateDir, "go_sessions")
	transcodedDir = filepath.Join(baseDir, "uploads", "transcoded")
	_ = os.MkdirAll(goSessionsDir, 0755)
	_ = os.MkdirAll(shmDir, 0755)

	apiID, _ = strconv.Atoi(os.Getenv("API_ID"))
	apiHash = os.Getenv("API_HASH")
	streamSecret = os.Getenv("STREAM_SECRET")
	if p := os.Getenv("GO_STREAM_PORT"); p != "" {
		listenAddr = "127.0.0.1:" + p
	}
	dbPass := os.Getenv("DB_PASS")
	dbDSN = fmt.Sprintf("rofi:%s@tcp(127.0.0.1:3306)/rofi_anirate?charset=utf8mb4&timeout=3s", dbPass)
	if db, err := sql.Open("mysql", dbDSN); err == nil {
		db.SetMaxOpenConns(8)
		db.SetMaxIdleConns(2)
		db.SetConnMaxLifetime(5 * time.Minute)
		dbPool = db
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	tokens := loadBotTokens()
	log.Printf("DarkMora Go Turbo Streamer v1.0 starting with %d bots on http://%s", len(tokens), listenAddr)
	pool.init(ctx, tokens)

	mux := http.NewServeMux()
	mux.HandleFunc("/stream/", handleStream)
	mux.HandleFunc("/stream", handleStream)
	mux.HandleFunc("/episode-proxy", handleStream)
	mux.HandleFunc("/episode-proxy.php", handleStream)
	mux.HandleFunc("/health", handleHealth)
	mux.HandleFunc("/pool", handleHealth)

	srv := &http.Server{
		Addr:              listenAddr,
		Handler:           mux,
		ReadHeaderTimeout: 10 * time.Second,
		IdleTimeout:       75 * time.Second,
	}

	go func() {
		<-ctx.Done()
		shCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = srv.Shutdown(shCtx)
	}()

	log.Printf("DarkMora Go Turbo Streamer ready on http://%s", listenAddr)
	if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatalf("HTTP server error: %v", err)
	}
}
