(function () {
  function el(html) {
    const tmp = document.createElement('div');
    tmp.innerHTML = html.trim();
    return tmp.firstChild;
  }

  const HISTORY_LIMIT = 16;
  const history = [];

  function init() {
    const root = document.getElementById('chat-widget-root');
    if (!root) return;

    const widget = el(`
      <div id="chatbot" class="chatbot-closed">
        <button id="chat-toggle" class="chat-toggle" type="button" aria-label="Open chat">💬</button>
        <div id="chat-panel" class="chat-panel" aria-hidden="true">
          <div class="chat-header">BookRec Chat</div>
          <div id="starters" class="chat-starters"></div>
          <div id="chat-messages" class="chat-messages"></div>
          <div class="chat-input">
            <input id="chat-input" placeholder="Ask about books, authors, themes..." type="text">
            <button id="chat-send" type="button">Send</button>
          </div>
        </div>
      </div>
    `);
    root.appendChild(widget);

    const toggle = document.getElementById('chat-toggle');
    const panel = document.getElementById('chat-panel');
    const startersEl = document.getElementById('starters');
    const messagesEl = document.getElementById('chat-messages');
    const input = document.getElementById('chat-input');
    const send = document.getElementById('chat-send');

    toggle.addEventListener('click', () => {
      const isOpen = panel.getAttribute('aria-hidden') === 'false';
      panel.setAttribute('aria-hidden', isOpen ? 'true' : 'false');
      if (!isOpen) loadStarters();
    });

    async function loadStarters() {
      const ctx = getPageContext();
      const url = '/api/chat/starters' + (ctx ? '?context=' + encodeURIComponent(ctx) : '');
      try {
        const r = await fetch(url);
        if (!r.ok) throw new Error('Failed to fetch starters');
        const arr = await r.json();
        startersEl.innerHTML = '';
        arr.forEach(s => {
          const btn = document.createElement('button');
          btn.className = 'starter btn btn-sm btn-outline-secondary me-1 mb-1';
          btn.textContent = s;
          btn.addEventListener('click', () => { input.value = s; sendMessage(); });
          startersEl.appendChild(btn);
        });
      } catch (e) {
        startersEl.textContent = 'Unable to load starters';
      }
    }

    function getPageContext() {
      const ctx = document.getElementById('page-context');
      const id = ctx && ctx.getAttribute('data-book-id');
      return id ? 'book:' + id : null;
    }

    async function sendMessage() {
      const text = input.value.trim();
      if (!text) return;
      appendMessage('You', text);
      input.value = '';
      pushHistory('user', text);
      try {
        const r = await fetch('/api/chat/message', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message: text,
            pageContext: getPageContext(),
            history: history.slice(0, -1)
          })
        });
        if (!r.ok) throw new Error('Failed to send message');
        const data = await r.json();
        const answer = data.answer || 'No answer';
        appendMessage('Assistant', answer);
        pushHistory('assistant', answer);
        if (data.sources && data.sources.length > 0) {
          const src = document.createElement('div');
          src.className = 'chat-sources';
          src.textContent = 'Sources: ' + data.sources.map(s => s.title || s).join(', ');
          messagesEl.appendChild(src);
        }
      } catch (e) {
        appendMessage('Assistant', 'I had an error contacting the chat service.');
      }
    }

    function pushHistory(role, content) {
      history.push({ role, content });
      if (history.length > HISTORY_LIMIT) {
        history.splice(0, history.length - HISTORY_LIMIT);
      }
    }

    function appendMessage(who, text) {
      const m = document.createElement('div');
      m.className = 'chat-msg';
      m.innerHTML = `<strong>${who}:</strong> <span>${escapeHtml(text)}</span>`;
      messagesEl.appendChild(m);
      messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    function escapeHtml(s) {
      return s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
    }

    send.addEventListener('click', sendMessage);
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') sendMessage(); });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
