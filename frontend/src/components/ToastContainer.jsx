import useToastStore from '../store/toastStore';
import Icon from './icons';

/**
 * The live region is always mounted, never conditionally rendered: assistive
 * technology only announces a change inside a region that existed before the
 * message arrived. The empty container is hidden by CSS
 * (.toast-container:empty), not by an early return.
 *
 * WCAG 2.2 4.1.3 Status Messages (AA).
 */
export default function ToastContainer() {
  const { toasts, removeToast } = useToastStore();

  return (
    <div
      className="toast-container"
      role="status"
      aria-live="polite"
      aria-atomic="false"
    >
      {toasts.map((t) => (
        <div
          key={t.id}
          className={`toast ${t.type}`}
          role={t.type === 'critical' ? 'alert' : undefined}
        >
          <span>{t.message}</span>
          <button
            type="button"
            className="toast__close"
            onClick={() => removeToast(t.id)}
            aria-label="Dismiss notification"
          >
            <Icon name="close" size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}
