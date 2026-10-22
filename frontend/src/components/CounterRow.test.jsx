import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import CounterRow from '../components/CounterRow';

describe('CounterRow', () => {
  it('renders each counter as a label with its value', () => {
    render(
      <CounterRow
        counters={[
          { label: 'Available Beds', value: '0/1' },
          { label: 'Active Staff', value: '5 / 5' },
        ]}
      />,
    );

    expect(screen.getByText('Available Beds')).toBeInTheDocument();
    expect(screen.getByText('0/1')).toBeInTheDocument();
    expect(screen.getByText('Active Staff')).toBeInTheDocument();
    expect(screen.getByText('5 / 5')).toBeInTheDocument();
  });

  it('zero-valued counters are neutral by default', () => {
    render(
      <CounterRow counters={[{ label: 'Checked-in Today', value: 0 }]} />,
    );

    const chip = screen.getByText('Checked-in Today').closest('[data-tone]');
    expect(chip).toHaveAttribute('data-tone', 'neutral');
  });

  it('an explicit tone colour-codes the value', () => {
    render(
      <CounterRow
        counters={[
          { label: 'Critical Cases', value: 3, tone: 'danger' },
          { label: 'Dis charged', value: 2, tone: 'success' },
          { label: 'Waiting', value: 9, tone: 'warning' },
          { label: 'In Lab', value: 4, tone: 'info' },
        ]}
      />,
    );

    expect(screen.getByText('Critical Cases').closest('[data-tone]')).toHaveAttribute('data-tone', 'danger');
    expect(screen.getByText('Dis charged').closest('[data-tone]')).toHaveAttribute('data-tone', 'success');
    expect(screen.getByText('Waiting').closest('[data-tone]')).toHaveAttribute('data-tone', 'warning');
    expect(screen.getByText('In Lab').closest('[data-tone]')).toHaveAttribute('data-tone', 'info');
  });

  it('an optional hint explains the value (unit-bearing counters)', () => {
    render(
      <CounterRow
        counters={[{ label: 'Present Today', value: 6, hint: 'checked-in' }]}
      />,
    );

    expect(screen.getByText('checked-in')).toBeInTheDocument();
  });

  it('renders nothing when there are no counters', () => {
    render(<CounterRow counters={[]} />);
    expect(document.querySelector('.counter-row')).toBeNull();
  });
});
