import EmptyState from "./states/EmptyState";
import ErrorState from "./states/ErrorState";
import LoadingState from "./states/LoadingState";
import { formatRelativeTimestamp } from "../utils/operations";

function CompactActivityFeed({ items = [], loading = false, error = "" }) {
  return (
    <section className="surface-card section-card compact-activity-section">
      <div className="section-header recent-incidents-header">
        <div>
          <div className="card-label">Recent Activity</div>
          <h2 className="section-title">Operational Feed</h2>
        </div>
      </div>

      {loading ? (
        <LoadingState message="Loading recent activity…" />
      ) : error ? (
        <ErrorState message={error} />
      ) : items.length === 0 ? (
        <EmptyState message="No recent platform activity yet." />
      ) : (
        <div className="compact-activity-list">
          {items.map((item) => (
            <article key={item.id} className="compact-activity-row">
              <span className={`status-pill ${item.tone} compact-activity-kind`}>
                <span className={`status-dot ${item.tone}`} />
                {item.kind}
              </span>
              <div className="compact-activity-copy">
                <div className="compact-activity-title">{item.title}</div>
                <div className="compact-activity-description">{item.description}</div>
              </div>
              <span className="compact-activity-time">{formatRelativeTimestamp(item.timestamp)}</span>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

export default CompactActivityFeed;
