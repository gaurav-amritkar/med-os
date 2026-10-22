import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import DataTable from '../components/DataTable';

const PATIENTS = [
  { id: 'p1', uhid: 'UHID000001', name: 'Anita Sharma', age: 30 },
  { id: 'p2', uhid: 'UHID000002', name: 'Ravi Rao', age: 41 },
];
const PAGE_META = { page: 0, size: 20, totalElements: 42, totalPages: 3 };

const baseProps = {
  columns: [
    { key: 'uhid', label: 'UHID' },
    { key: 'name', label: 'Name' },
  ],
  rows: PATIENTS,
  rowKey: (row) => row.id,
};

describe('DataTable', () => {
  it('renders uppercase column labels and one row per entry', () => {
    render(<DataTable {...baseProps} />);

    expect(screen.getByText('UHID')).toBeInTheDocument();
    expect(screen.getByText('UHID000002')).toBeInTheDocument();
    // Cell values render, including via a custom renderer
    expect(screen.getByText('Anita Sharma')).toBeInTheDocument();
  });

  it('uses a column renderer when provided', () => {
    render(
      <DataTable
        {...baseProps}
        columns={[
          { key: 'uhid', label: 'UHID' },
          { key: 'age', label: 'Age', render: (row) => `${row.age} yrs` },
        ]}
      />,
    );

    expect(screen.getByText('30 yrs')).toBeInTheDocument();
    expect(screen.getByText('41 yrs')).toBeInTheDocument();
  });

  it('shows the specific empty state, never a bare "No data"', () => {
    render(
      <DataTable
        {...baseProps}
        rows={[]}
        emptyMessage="No appointments scheduled for the selected filters."
      />,
    );

    expect(screen.getByText('No appointments scheduled for the selected filters.')).toBeInTheDocument();
  });

  it('a failed fetch is not an empty result — failure wins and is role=alert', () => {
    render(
      <DataTable
        {...baseProps}
        rows={[]}
        error="Could not load patients. The server did not respond as expected."
        emptyMessage="No patients found."
      />,
    );

    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Could not load patients');
  });

  it('loading replaces the table contents with the loading indicator', () => {
    render(<DataTable {...baseProps} loading loadingLabel="Loading patients..." />);

    expect(screen.getByText('Loading patients...')).toBeInTheDocument();
    expect(screen.queryByText('Anita Sharma')).not.toBeInTheDocument();
  });

  it('renders the pagination footer with range and page position when pageMeta is present', () => {
    render(
      <DataTable {...baseProps} pageMeta={PAGE_META} onPageChange={vi.fn()} />,
    );

    // Grammar #7: range of results and page position, owned by the component.
    expect(screen.getByText(/1–2 of 42/)).toBeInTheDocument();
    expect(screen.getByText(/page 1 of 3/)).toBeInTheDocument();
  });

  it('Prev is disabled on the first page; Next requests the next page', async () => {
    const onPageChange = vi.fn();
    const user = userEvent.setup();
    render(<DataTable {...baseProps} pageMeta={PAGE_META} onPageChange={onPageChange} />);

    const prev = screen.getByRole('button', { name: /previous page/i });
    const next = screen.getByRole('button', { name: /next page/i });
    expect(prev).toBeDisabled();

    await user.click(next);
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('no footer when there is a single page', () => {
    render(
      <DataTable
        {...baseProps}
        pageMeta={{ ...PAGE_META, totalPages: 1 }}
        onPageChange={vi.fn()}
      />,
    );

    expect(screen.queryByRole('button', { name: /next page/i })).not.toBeInTheDocument();
  });

  it('rows are clickable when onRowClick is set', async () => {
    const onRowClick = vi.fn();
    const user = userEvent.setup();
    render(<DataTable {...baseProps} onRowClick={onRowClick} />);

    await user.click(screen.getByText('Ravi Rao'));
    expect(onRowClick).toHaveBeenCalledWith(PATIENTS[1]);
  });
});
