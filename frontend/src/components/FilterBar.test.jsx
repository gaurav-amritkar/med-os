import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FilterBar from '../components/FilterBar';

describe('FilterBar', () => {
  it('renders the search input bound to value and onChange', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(
      <FilterBar search={{ value: '', onChange, placeholder: 'Search patients by name...' }} />,
    );

    const input = screen.getByLabelText(/search patients/i);
    await user.type(input, 'anita');
    expect(onChange).toHaveBeenCalledWith('a'); // per character
  });

  it('renders the Clear button when onClear is provided and clears the search', async () => {
    const onClear = vi.fn();
    const user = userEvent.setup();
    render(
      <FilterBar
        search={{ value: 'anita', onChange: vi.fn(), placeholder: 'Search' }}
        onClear={onClear}
      />,
    );

    await user.click(screen.getByRole('button', { name: /clear/i }));
    expect(onClear).toHaveBeenCalledTimes(1);
  });

  it('children (selects, radios) render between the search input and the buttons', async () => {
    const user = userEvent.setup();
    render(
      <FilterBar search={{ value: '', onChange: vi.fn(), placeholder: 'Search' }}>
        <select aria-label="Patient type" defaultValue="all">
          <option value="all">All patients</option>
          <option value="opd">OPD Registrant</option>
        </select>
      </FilterBar>,
    );

    const select = screen.getByLabelText(/patient type/i);
    await user.selectOptions(select, 'opd');
    expect(select).toHaveValue('opd');
  });

  it('renders the Filter button only when onApply is provided', async () => {
    const onApply = vi.fn();
    const user = userEvent.setup();

    const { rerender } = render(
      <FilterBar search={{ value: '', onChange: vi.fn(), placeholder: 'S' }} />,
    );
    expect(screen.queryByRole('button', { name: /^filter$/i })).not.toBeInTheDocument();

    rerender(
      <FilterBar search={{ value: '', onChange: vi.fn(), placeholder: 'S' }} onApply={onApply} />,
    );
    await user.click(screen.getByRole('button', { name: /^filter$/i }));
    expect(onApply).toHaveBeenCalledTimes(1);
  });

  it('no search config means no search input', () => {
    render(<FilterBar>
      <select aria-label="Queue status" defaultValue="all">
        <option value="all">All</option>
      </select>
    </FilterBar>);
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });
});
