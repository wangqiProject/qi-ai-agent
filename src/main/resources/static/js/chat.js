let currentMode = 'stream';

const messagesContainer = document.getElementById('chatMessages');
const messageInput = document.getElementById('messageInput');
const sendBtn = document.getElementById('sendBtn');
const streamBtn = document.getElementById('streamBtn');
const syncBtn = document.getElementById('syncBtn');

// 切换模式
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

// 发送消息
async function sendMessage() {
    const message = messageInput.value.trim();
    if (!message) return;

    // 禁用输入
    setInputDisabled(true);

    // 添加用户消息
    appendMessage(message, 'user');
    messageInput.value = '';

    // 滚动到底部
    scrollToBottom();

    try {
        if (currentMode === 'stream') {
            await sendStream(message);
        } else {
            await sendSync(message);
        }
    } catch (e) {
        appendMessage('网络错误：' + e.message, 'assistant');
    } finally {
        setInputDisabled(false);
        messageInput.focus();
    }
}

// 同步请求
async function sendSync(message) {
    const response = await fetch('/api/chat/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
    });
    const data = await response.json();
    appendMessage(data.reply, 'assistant');
}

// 流式请求
async function sendStream(message) {
    const response = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
    });

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let bubble = createStreamingBubble();
    let fullText = '';
    let buffer = '';

    while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        // 保留最后一个不完整的行到下一次读取
        buffer = lines.pop() || '';

        for (const line of lines) {
            if (line.startsWith('data:')) {
                const text = line.substring(5).trim();
                if (text) {
                    fullText += text;
                    bubble.textContent = fullText;
                    scrollToBottom();
                }
            }
        }
    }

    // 流式结束，移除闪烁光标
    bubble.classList.remove('streaming');
}

// 添加消息
function appendMessage(text, role) {
    const div = document.createElement('div');
    div.className = `message ${role}`;
    div.textContent = text;
    messagesContainer.appendChild(div);
    scrollToBottom();
}

// 创建流式消息气泡
function createStreamingBubble() {
    const div = document.createElement('div');
    div.className = 'message assistant streaming';
    div.textContent = '';
    messagesContainer.appendChild(div);
    scrollToBottom();
    return div;
}

// 禁用/启用输入
function setInputDisabled(disabled) {
    messageInput.disabled = disabled;
    sendBtn.disabled = disabled;
}

// 滚动到底部
function scrollToBottom() {
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
}

// 回车发送
messageInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});