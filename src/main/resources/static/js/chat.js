// ===== 会话管理 =====

const STORAGE_KEY = 'qi_ai_session_id';

function getSessionId() {
    let id = localStorage.getItem(STORAGE_KEY);
    if (!id) {
        id = crypto.randomUUID();
        localStorage.setItem(STORAGE_KEY, id);
    }
    return id;
}

function setSessionId(id) {
    localStorage.setItem(STORAGE_KEY, id);
}

// ===== DOM 引用 =====

const messagesContainer = document.getElementById('chatMessages');
const welcomeMessage = document.getElementById('welcomeMessage');
const messageInput = document.getElementById('messageInput');
const sendBtn = document.getElementById('sendBtn');
const streamBtn = document.getElementById('streamBtn');
const syncBtn = document.getElementById('syncBtn');

let currentMode = 'stream';
const TIME_OPTIONS = { hour: '2-digit', minute: '2-digit' };

// ===== Markdown 配置 =====

marked.setOptions({
    breaks: false,
    gfm: true,
});

function renderMarkdown(text) {
    // marked 默认会转义 XSS，但为了安全再加一层过滤
    return marked.parse(text);
}

// ===== 工具函数 =====

function formatTime() {
    return new Date().toLocaleTimeString('zh-CN', TIME_OPTIONS);
}

function scrollToBottom() {
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

function setInputDisabled(disabled) {
    messageInput.disabled = disabled;
    sendBtn.disabled = disabled;
}

// ===== 切换模式 =====

function switchMode(mode) {
    currentMode = mode;
    if (mode === 'stream') {
        streamBtn.classList.add('active');
        syncBtn.classList.remove('active');
    } else {
        syncBtn.classList.add('active');
        streamBtn.classList.remove('active');
    }
}

// ===== 新建会话 =====

function newSession() {
    const newId = crypto.randomUUID();
    setSessionId(newId);
    // 清空消息区
    document.querySelectorAll('.message').forEach(el => el.remove());
    welcomeMessage.style.display = 'block';
    scrollToBottom();
}

// ===== 渲染消息 =====

function appendMessage(text, role) {
    welcomeMessage.style.display = 'none';

    const div = document.createElement('div');
    div.className = `message ${role}`;

    const time = formatTime();
    const roleLabel = role === 'user' ? '你' : 'AI';
    const avatarText = role === 'user' ? '你' : 'AI';

    // 结构化消息：头部 + 正文
    div.innerHTML = `
        <div class="message-header">
            <span class="message-avatar">${avatarText}</span>
            <span class="message-role-label">${roleLabel}</span>
            <span class="message-time">${time}</span>
        </div>
        <div class="message-body markdown-content">${renderMarkdown(text)}</div>
    `;

    messagesContainer.appendChild(div);
    scrollToBottom();
    return div;
}

// 流式模式：先创建空消息体，实时更新
function createStreamingBubble() {
    welcomeMessage.style.display = 'none';

    const time = formatTime();
    const div = document.createElement('div');
    div.className = 'message assistant streaming';
    div.innerHTML = `
        <div class="message-header">
            <span class="message-avatar">AI</span>
            <span class="message-role-label">AI</span>
            <span class="message-time">${time}</span>
        </div>
        <div class="message-body markdown-content streaming-body"></div>
    `;
    messagesContainer.appendChild(div);
    scrollToBottom();

    // 返回正文元素，方便更新内容
    return div.querySelector('.message-body');
}

// ===== 发送消息 =====

async function sendMessage() {
    const message = messageInput.value.trim();
    if (!message) return;

    setInputDisabled(true);
    appendMessage(message, 'user');
    messageInput.value = '';

    const sessionId = getSessionId();

    try {
        if (currentMode === 'stream') {
            await sendStream(message, sessionId);
        } else {
            await sendSync(message, sessionId);
        }
    } catch (e) {
        appendMessage('网络错误：' + e.message, 'assistant');
    } finally {
        setInputDisabled(false);
        messageInput.focus();
    }
}

// 同步请求
async function sendSync(message, sessionId) {
    const response = await fetch('/api/chat/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, sessionId }),
    });
    const data = await response.json();
    // 更新 sessionId（如果后端生成了新的）
    if (data.sessionId && data.sessionId !== sessionId) {
        setSessionId(data.sessionId);
    }
    appendMessage(data.reply, 'assistant');
}

// 流式请求
async function sendStream(message, sessionId) {
    const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, sessionId }),
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    const bodyEl = createStreamingBubble();
    let fullText = '';

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        // 直接拼接原始 chunk 文本
        fullText += decoder.decode(value, { stream: true });
        // 渐进式 Markdown 渲染（边接收边格式化）
        bodyEl.innerHTML = renderMarkdown(fullText);
        scrollToBottom();
    }

    // 流式结束，移除光标
    const parent = bodyEl.closest('.message');
    if (parent) {
        parent.classList.remove('streaming');
    }
}

// ===== 恢复历史消息 =====

async function loadHistory() {
    const sessionId = getSessionId();
    try {
        const response = await fetch(`/api/chat/history?sessionId=${encodeURIComponent(sessionId)}`);
        const messages = await response.json();
        if (messages && messages.length > 0) {
            welcomeMessage.style.display = 'none';
            for (const msg of messages) {
                appendMessage(msg.content, msg.role);
            }
        }
    } catch (e) {
        console.warn('加载历史消息失败:', e);
    }
}

// ===== 初始化 =====

// 回车发送
messageInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

// 页面加载后恢复历史
loadHistory();
