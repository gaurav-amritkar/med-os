import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { expect, it, vi } from 'vitest';
import Admissions from './Admissions';
import { admissionApi, patientApi } from '../api';

vi.mock('../api', () => ({
  admissionApi: { rooms: vi.fn(), active: vi.fn() },
  patientApi: { list: vi.fn() },
}));

it('shows an outage instead of zero beds and reloads the ward on retry', async () => {
  admissionApi.rooms.mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ data: [] });
  admissionApi.active.mockResolvedValue({ data: [] });
  patientApi.list.mockResolvedValue({ data: [] });

  render(<Admissions />);
  expect(screen.getByRole('status')).toHaveTextContent('Loading wards');
  const alert = await screen.findByRole('alert');
  expect(screen.queryByText('Total Beds')).toBeNull();
  await userEvent.click(within(alert).getByRole('button', { name: 'Retry' }));

  expect(await screen.findByText('Total Beds')).toBeVisible();
  expect(alert).not.toBeInTheDocument();
  expect(admissionApi.rooms).toHaveBeenCalledTimes(2);
});
