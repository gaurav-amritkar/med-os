import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';

const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

/**
 * The single dialog used by the application.
 *
 * <p>A modal that only looks like one traps sighted mouse users and strands
 * keyboard and screen-reader users behind it: the page underneath stays in the
 * tab order, Escape does nothing, and focus is lost when the dialog closes.
 * Those are not per-page concerns, so the behaviour lives here instead of being
 * re-implemented fourteen times.
 *
 * <p>Contract:
 * <ul>
 *   <li>rendered through a portal on {@code document.body}, so a page's
 *       {@code overflow}/{@code transform} cannot clip it;</li>
 *   <li>{@code role="dialog"}, {@code aria-modal="true"}, and a name derived from
 *       the visible heading ({@code aria-labelledby}) — the label always matches
 *       what the user sees;</li>
 *   <li>focus moves to the first control on open and returns to the trigger on
 *       close;</li>
 *   <li>Tab and Shift+Tab cycle inside the dialog;</li>
 *   <li>Escape and a backdrop click both call {@code onClose};</li>
 *   <li>the page behind cannot scroll while the dialog is open.</li>
 * </ul>
 *
 * <p>Callers render the modal conditionally — {@code {open && <Modal .../>}} — so
 * mount is also open. The header (title plus close button) is rendered here to
 * keep every dialog identical.
 */
export default function Modal({ title, onClose, children, size = 'default' }) {
  const dialogRef = useRef(null);
  const onCloseRef = useRef(onClose);
  const titleId = useId();

  // Callers pass inline arrows; keeping the latest in a ref means the mount
  // effect below never re-runs and re-steals focus on an unrelated re-render.
  onCloseRef.current = onClose;

  useEffect(() => {
    const dialog = dialogRef.current;
    const previouslyFocused = document.activeElement;
    const previousOverflow = document.body.style.overflow;

    const focusable = () => Array.from(dialog.querySelectorAll(FOCUSABLE));

    // Start on the first real control, not the header's close button: a user who
    // opened a form wants to type, and the trap below keeps Close reachable.
    const controls = focusable();
    const initial = controls.find((element) => !element.hasAttribute('data-modal-close'));
    (initial || controls[0] || dialog).focus();

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab') return;

      const controls = focusable();
      if (controls.length === 0) {
        event.preventDefault();
        dialog.focus();
        return;
      }
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    document.body.style.overflow = 'hidden';

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.body.style.overflow = previousOverflow;
      if (previouslyFocused instanceof HTMLElement && previouslyFocused.isConnected) {
        previouslyFocused.focus();
      }
    };
  }, []);

  const handleBackdropClick = (event) => {
    // Only a click on the scrim itself closes. Clicks inside the dialog bubble
    // here with a different target, so no stopPropagation is needed below.
    if (event.target === event.currentTarget) {
      onClose();
    }
  };

  return createPortal(
    <div className="modal-overlay" onClick={handleBackdropClick}>
      <div
        ref={dialogRef}
        className={size === 'narrow' ? 'modal modal--narrow' : 'modal'}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <div className="modal-header">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="btn-ghost btn-sm" onClick={onClose}
            aria-label="Close" data-modal-close>
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body
  );
}
