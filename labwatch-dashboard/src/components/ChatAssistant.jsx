import { useEffect, useRef, useState } from "react";

const SUGGESTED_PROMPTS = [
  "Summarize current health",
  "Why is CPU elevated?",
  "Which alert matters most?",
  "What should I fix first?",
];

function ChatBubble({ role, content }) {
  return <div className={`assistant-bubble ${role}`}>{content}</div>;
}

function SuggestionPrompt({ label, onClick }) {
  return (
    <button type="button" className="suggestion-chip" onClick={onClick}>
      {label}
    </button>
  );
}

function ChatAssistant({
  onSendMessage,
  isOpen = true,
  onToggleOpen,
  triggerMessage = null,
  variant = "floating",
  title = "AI Assistant",
  subtitle = "Ask about system behavior",
  contextPanel = null,
}) {
  const isPageVariant = variant === "page";
  const [inputValue, setInputValue] = useState("");
  const [messages, setMessages] = useState([
    {
      id: "welcome",
      role: "assistant",
      content: "Ask about spikes, anomalies, root cause, or what to do next.",
    },
  ]);
  const [loading, setLoading] = useState(false);
  const [lastFailedMessage, setLastFailedMessage] = useState("");
  const lastTriggerIdRef = useRef(null);
  const messagesEndRef = useRef(null);

  const handleSend = async (messageOverride) => {
    const message = (messageOverride ?? inputValue).trim();
    if (!message || loading) {
      return;
    }

    const userMessage = {
      id: `user-${Date.now()}`,
      role: "user",
      content: message,
    };

    setMessages((existing) => [...existing, userMessage]);
    setInputValue("");
    setLoading(true);
    setLastFailedMessage("");

    try {
      const response = await onSendMessage(message);
      setMessages((existing) => [
        ...existing,
        {
          id: `assistant-${Date.now()}`,
          role: "assistant",
          content: response,
        },
      ]);
    } catch {
      setLastFailedMessage(message);
      setMessages((existing) => [
        ...existing,
        {
          id: `assistant-fallback-${Date.now()}`,
          role: "assistant",
          content: "AI is temporarily unavailable. Retry this request or continue using the system context panel.",
        },
      ]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, loading]);

  useEffect(() => {
    if (!triggerMessage?.id || !triggerMessage?.message) {
      return;
    }

    if (lastTriggerIdRef.current === triggerMessage.id) {
      return;
    }

    lastTriggerIdRef.current = triggerMessage.id;
    handleSend(triggerMessage.message);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [triggerMessage]);

  if (!isPageVariant && !isOpen) {
    return (
      <button
        type="button"
        className="primary-button"
        style={{ bottom: "24px", position: "fixed", right: "24px", zIndex: 50 }}
        onClick={() => onToggleOpen?.(true)}
      >
        AI Assistant
      </button>
    );
  }

  const shouldShowSuggestions = messages.length <= 1;

  const assistantPanel = (
    <div className="assistant-panel">
      <div className="assistant-header">
        <div className="card-label">{title}</div>
        <div className="section-title">{subtitle}</div>
      </div>

      <div className="assistant-messages">
        {messages.map((message) => (
          <ChatBubble key={message.id} role={message.role} content={message.content} />
        ))}
        {loading ? <ChatBubble role="assistant" content="Thinking…" /> : null}
        <div ref={messagesEndRef} />
      </div>

      <div className="assistant-composer">
        {triggerMessage?.message ? (
          <div className="machine-card-subtle">Context-driven prompt sent automatically</div>
        ) : null}
        {shouldShowSuggestions ? (
          <div className="assistant-suggestions">
            {SUGGESTED_PROMPTS.map((prompt) => (
              <SuggestionPrompt key={prompt} label={prompt} onClick={() => handleSend(prompt)} />
            ))}
          </div>
        ) : null}
        {lastFailedMessage ? (
          <button type="button" className="ghost-button" onClick={() => handleSend(lastFailedMessage)}>
            Retry last request
          </button>
        ) : null}
        <textarea
          className="assistant-textarea"
          value={inputValue}
          onChange={(event) => setInputValue(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              handleSend();
            }
          }}
          placeholder="Ask for root cause, triage advice, or a short health summary"
          rows={3}
        />
        <button
          type="button"
          className="action-button"
          disabled={loading || !inputValue.trim()}
          onClick={() => handleSend()}
          style={loading || !inputValue.trim() ? { cursor: "not-allowed", filter: "grayscale(0.25)", opacity: 0.7 } : undefined}
        >
          Send
        </button>
      </div>
    </div>
  );

  if (isPageVariant) {
    return (
      <div className="assistant-layout">
        {assistantPanel}
        {contextPanel ? <aside className="context-panel">{contextPanel}</aside> : null}
      </div>
    );
  }

  return (
    <>
      <button
        type="button"
        className="primary-button"
        style={{ bottom: "24px", position: "fixed", right: "24px", zIndex: 50 }}
        onClick={() => onToggleOpen?.(!isOpen)}
      >
        {isOpen ? "Close Assistant" : "AI Assistant"}
      </button>
      {isOpen ? (
        <div style={{ bottom: "88px", position: "fixed", right: "24px", width: "380px", zIndex: 50 }}>
          {assistantPanel}
        </div>
      ) : null}
    </>
  );
}

export default ChatAssistant;
