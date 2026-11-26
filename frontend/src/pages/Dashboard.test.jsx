import { expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Dashboard from './Dashboard';
import { dashboardApi } from '../api';

vi.mock('../api', () => ({ dashboardApi: { dashboard: vi.fn(), notifications: vi.fn() } }));

it('keeps notifications visible during a statistics outage and restores counts on retry', async () => {
  dashboardApi.dashboard.mockRejectedValueOnce(new Error('unavailable'))
    .mockResolvedValueOnce({ data: { totalPatients: 37, pendingInvoices: 0 } });
  dashboardApi.notifications.mockResolvedValue({ data: [{ id: 1, title: 'Stock alert' }] });
  render(<MemoryRouter><Dashboard /></MemoryRouter>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Hospital counts unavailable');
  expect(await screen.findByText('Stock alert')).toBeVisible();
  expect(screen.queryByText('0')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'Retry hospital counts' }));
  expect(await screen.findByText('37')).toBeVisible();
  expect(screen.getByText('0')).toBeVisible();
});
