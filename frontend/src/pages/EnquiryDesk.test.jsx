import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import EnquiryDesk from './EnquiryDesk';
import { enquiryApi } from '../api';
import useToastStore from '../store/toastStore';

vi.mock('../api', () => ({
  enquiryApi: { list: vi.fn(), summary: vi.fn(), create: vi.fn(), updateStatus: vi.fn() },
}));

/**
 * The desk board, per MedWorks grammar verbatim: the counter rail answers the
 * day's question at a glance, actions move an enquiry to where it must go next
 * (and refuse to offer actions that would break the lifecycle), and logging a
 * contact never requires a page change.
 */
const ENQUIRIES = [
  {
    id: 'e1', enquiryNo: 1, enquiryRef: 'ENQ-1', contactName: 'Anita Sharma',
    contactPhone: '+91 98765 11111', type: 'appointment', department: 'Cardiology',
    reason: 'Wants a cardiology consultation', status: 'new_enquiry', notes: null,
  },
  {
    id: 'e2', enquiryNo: 2, enquiryRef: 'ENQ-2', contactName: 'Ravi Menon',
    contactPhone: null, type: 'general', department: null, reason: 'Health check-up price list',
    status: 'follow_up', notes: 'call back Friday',
  },
];

const PAGE = { page: 0, size: 20, totalElements: 2, totalPages: 1, first: true, last: true };

const SUMMARY = { total: 2, newEnquiries: 1, followUp: 1, converted: 0, cancelled: 0 };

function renderPage() {
  return render(
    <MemoryRouter>
      <EnquiryDesk />
    </MemoryRouter>
  );
}

describe('EnquiryDesk board', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useToastStore.setState({ toasts: [] });
    enquiryApi.list.mockResolvedValue({ data: ENQUIRIES, meta: PAGE });
    enquiryApi.summary.mockResolvedValue({ data: SUMMARY });
  });

  it('renders the counter rail with the MedWorks labels and live values', async () => {
    renderPage();

    await waitFor(() => expect(enquiryApi.summary).toHaveBeenCalled());
    const chip = (label) => screen.getByText('Total').closest('ul')
      .querySelector(`.counter-chip[data-tone]`)
      && screen.getByText(label, { selector: '.counter-label' }).closest('li');
    const total = chip('Total');
    expect(total).toHaveTextContent('2');
    expect(chip('New')).toHaveTextContent('1');
    expect(chip('Follow-up')).toHaveTextContent('1');
    expect(chip('Converted')).toHaveTextContent('0');
    expect(chip('Cancelled')).toHaveTextContent('0');
  });

  it('lists enquiries with the Enquiry # / Contact / Reason-Department grammar', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('ENQ-1')).toBeInTheDocument());
    expect(screen.getByText('Anita Sharma · +91 98765 11111')).toBeInTheDocument();
    expect(screen.getByText('Wants a cardiology consultation')).toBeInTheDocument();
    const cardiologyBadge = screen.getAllByText('Cardiology')[0];
    expect(cardiologyBadge).toBeInTheDocument();
    expect(screen.getByText('Follow-up', { selector: '.badge' })).toBeInTheDocument();
  });

  it('offers only the lifecycle actions a row can legally take', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByText('ENQ-1')).toBeInTheDocument());
    // New enquiry: follow up / convert / cancel.
    expect(screen.getByLabelText('Follow up ENQ-1')).toBeInTheDocument();
    expect(screen.getByLabelText('Convert ENQ-1')).toBeInTheDocument();
    expect(screen.getByLabelText('Cancel ENQ-1')).toBeInTheDocument();
    // Follow-up: no second follow-up offered; convert and cancel still are.
    expect(screen.queryByLabelText('Follow up ENQ-2')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Convert ENQ-2')).toBeInTheDocument();
  });

  it('logs an enquiry from the modal and refreshes the board', async () => {
    const user = userEvent.setup();
    enquiryApi.create.mockResolvedValue({ data: {} });
    renderPage();

    await waitFor(() => expect(screen.getByText('ENQ-1')).toBeInTheDocument());
    await user.click(screen.getByText('+ Log Enquiry'));
    await user.type(screen.getByLabelText('Contact name'), 'Prema Iyer');
    await user.type(screen.getByLabelText('Phone'), '+91 98765 33333');
    await user.selectOptions(screen.getByLabelText('Type'), 'admission');
    await user.type(screen.getByLabelText('Reason'), 'Elective admission next week');
    await user.click(screen.getByRole('button', { name: 'Log Enquiry' }));

    await waitFor(() => {
      expect(enquiryApi.create).toHaveBeenCalledWith(expect.objectContaining({
        contactName: 'Prema Iyer',
        contactPhone: '+91 98765 33333',
        type: 'admission',
        reason: 'Elective admission next week',
      }));
    });
    expect(useToastStore.getState().toasts[0].type).toBe('success');
  });

  it('keeps the form open and says so when logging fails', async () => {
    const user = userEvent.setup();
    enquiryApi.create.mockRejectedValue({
      response: { data: { message: 'Contact name is required' } },
    });
    renderPage();

    await waitFor(() => expect(screen.getByText('ENQ-1')).toBeInTheDocument());
    await user.click(screen.getByText('+ Log Enquiry'));
    await user.type(screen.getByLabelText('Reason'), 'some reason');
    await user.click(screen.getByRole('button', { name: 'Log Enquiry' }));

    await waitFor(() => {
      expect(useToastStore.getState().toasts[0]).toMatchObject({
        type: 'critical', message: 'Contact name is required',
      });
    });
    // The half-typed enquiry is still on screen — nothing was thrown away.
    expect(screen.getByLabelText('Reason')).toBeInTheDocument();
  });

  it('moves an enquiry to follow-up with the promised context in the note', async () => {
    const user = userEvent.setup();
    enquiryApi.updateStatus.mockResolvedValue({ data: {} });
    renderPage();

    await waitFor(() => expect(screen.getByText('ENQ-1')).toBeInTheDocument());
    await user.click(screen.getByLabelText('Follow up ENQ-1'));
    expect(screen.getByRole('dialog', { name: 'Enquiry status note' })).toBeInTheDocument();
    await user.type(screen.getByTestId('enq-note'), 'spoke to her, call back Tuesday');
    await user.click(screen.getByRole('button', { name: 'Follow-up' }));

    await waitFor(() => {
      expect(enquiryApi.updateStatus).toHaveBeenCalledWith('e1', {
        status: 'follow_up', note: 'spoke to her, call back Tuesday',
      });
    });
  });

  it('states a specific empty state, not a bare "No data"', async () => {
    enquiryApi.list.mockResolvedValue({
      data: [], meta: { page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true },
    });
    enquiryApi.summary.mockResolvedValue({
      data: { total: 0, newEnquiries: 0, followUp: 0, converted: 0, cancelled: 0 },
    });

    renderPage();

    await waitFor(() => {
      expect(screen.getByText('No enquiries on the desk yet — log the first one.')).toBeInTheDocument();
    });
  });

  it('renders a failure as an alert, never as an empty result', async () => {
    enquiryApi.list.mockRejectedValue({ response: { status: 500, data: {} } });

    renderPage();

    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByRole('alert').textContent).toMatch(/could not load the enquiry board/i);
  });
});
