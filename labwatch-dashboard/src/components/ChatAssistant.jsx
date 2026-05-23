import { useEffect, useRef, useState } from "react";

const SUGGESTED_PROMPTS = [
  "Summarize current health",
  "Why is CPU elevated?",
  "Which alert matters most?",
  "What should I fix first?",
];

const SECTION_LABELS = {
  "WHAT'S HAPPENING": "What’s Happening",
  "WHATS HAPPENING": "What’s Happening",
  "WHAT IS HAPPENING": "What’s Happening",
  "WHY THIS HAPPENS": "Why This Happens",
  "IS IT SERIOUS?": "Is It Serious?",
  "IS IT SERIOUS": "Is It Serious?",
  "WHAT TO DO NEXT": "Recommended Checks",
  "RECOMMENDED CHECKS": "Recommended Checks",
};

function normalizeSectionLabel(line = "") {
  return line
    .replace(/^#+\s*/, "")
    .replace(/:$/, "")
    .trim()
    .toUpperCase();
}

function parseAssistantSections(content = "") {
  const lines = String(content).split("\n");
  const sections = [];
  let current = null;

  lines.forEach((rawLine) => {
    const line = rawLine.trim();
    const label = SECTION_LABELS[normalizeSectionLabel(line)];

    if (label) {
      if (current) {
        sections.push(current);
      }
      current = { label, lines: [] };
      return;
    }

    if (current && line) {
      current.lines.push(line);
    }
  });

  if (current) {
    sections.push(current);
  }

  return sections.length >= 2 ? sections : [];
}

function cleanAssistantLine(line = "") {
  return line
    .replace(/^Recommended checks:\s*/i, "")
    .replace(/^\s*[-*]\s*/, "")
    .trim();
}

function AssistantSection({ section }) {
  const isChecklist = section.label === "Recommended Checks";
  const lines = section.lines.map(cleanAssistantLine).filter(Boolean);

  return (
    <section className={`assistant-response-section${isChecklist ? " checklist" : ""}`}>
      <div className="assistant-response-label">{section.label}</div>
      {isChecklist ? (
        <ul className="assistant-response-list">
          {lines.map((line) => (
            <li key={`${section.label}-${line}`}>{line}</li>
          ))}
        </ul>
      ) : (
        <p>{lines.join(" ") || "No detail available yet."}</p>
      )}
    </section>
  );
}

function AssistantMessageContent({ content }) {
  const sections = parseAssistantSections(content);

  if (sections.length === 0) {
    return <span>{content}</span>;
  }

  return (
    <div className="assistant-response-card">
      {sections.map((section) => (
        <AssistantSection key={section.label} section={section} />
      ))}
    </div>
  );
}

function ChatBubble({ role, content }) {
  return (
    <div className={`assistant-bubble ${role}`}>
      {role === "assistant" ? <AssistantMessageContent content={content} /> : content}
    </div>
  );
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
