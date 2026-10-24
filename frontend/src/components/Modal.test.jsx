import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Modal from './Modal';

function renderModal(overrides = {}) {
  const props = {
    title: 'Add Staff',
    onClose: vi.fn(),
    children: (
      <>
        <input aria-label="Full name" />
        <button type="button">Save</button>
      </>
    ),
    ...overrides,
  };
  return { ...render(<Modal {...props} />), props };
}

describe('Modal', () => {
  it('is a labelled, modal dialog with the visible title as its name', () => {
    renderModal();
    const dialog = screen.getByRole('dialog', { name: 'Add Staff' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAttribute('aria-labelledby');
    expect(dialog).toContainElement(screen.getByRole('heading', { name: 'Add Staff' }));
  });

  it('renders through a portal on document.body, not inside the page tree', () => {
    const { container } = renderModal();
    expect(container).toBeEmptyDOMElement();
    expect(document.body).toContainElement(screen.getByRole('dialog'));
  });

  it('moves focus into the dialog and returns it to the trigger on close', () => {
    const trigger = document.createElement('button');
    trigger.textContent = 'Open';
    document.body.appendChild(trigger);
    trigger.focus();

    const { unmount } = renderModal();
    expect(screen.getByLabelText('Full name')).toHaveFocus();

    unmount();
    expect(trigger).toHaveFocus();
    trigger.remove();
  });

  it('closes on Escape', async () => {
    const { props } = renderModal();
    await userEvent.keyboard('{Escape}');
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('closes on a backdrop click but not on a click inside the dialog', async () => {
    const { props } = renderModal();
    const dialog = screen.getByRole('dialog');

    await userEvent.click(screen.getByLabelText('Full name'));
    expect(props.onClose).not.toHaveBeenCalled();

    // The overlay is the dialog's parent.
    fireEvent.click(dialog.parentElement);
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('closes from the header close button', async () => {
    const { props } = renderModal();
    await userEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(props.onClose).toHaveBeenCalledTimes(1);
  });

  it('cycles Tab inside the dialog', async () => {
    renderModal();
    const input = screen.getByLabelText('Full name');
    const save = screen.getByRole('button', { name: 'Save' });
    const close = screen.getByRole('button', { name: 'Close' });

    // Focus starts on the first body control; the trap keeps Close reachable.
    expect(input).toHaveFocus();
    await userEvent.tab();
    expect(save).toHaveFocus();
    await userEvent.tab();
    expect(close).toHaveFocus();
    await userEvent.tab();
    expect(input).toHaveFocus();

    await userEvent.tab({ shift: true });
    expect(close).toHaveFocus();
  });

  it('locks page scroll while open and restores it on unmount', () => {
    document.body.style.overflow = 'auto';
    const { unmount } = renderModal();
    expect(document.body.style.overflow).toBe('hidden');
    unmount();
    expect(document.body.style.overflow).toBe('auto');
  });
});
