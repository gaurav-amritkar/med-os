import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import Header from './Header';
import useAuthStore from '../store/authStore';

describe('Header', () => {
  beforeEach(() => {
    sessionStorage.clear();
    useAuthStore.setState({
      token: 't',
      user: { username: 'admin', fullName: 'System Administrator', role: 'admin' },
      role: 'admin',
      tenantId: 'ten-1',
      tenantName: 'Clinic A',
    });
  });

  it('shows the active tenant name so staff know which hospital they are in', () => {
    render(<Header navOpen={false} onMenuClick={vi.fn()} />);
    expect(screen.getByText('Clinic A')).toBeInTheDocument();
  });

  it('omits the tenant line when no tenant name is available', () => {
    useAuthStore.setState({ tenantName: null });
    render(<Header navOpen={false} onMenuClick={vi.fn()} />);
    expect(screen.queryByText('Clinic A')).not.toBeInTheDocument();
  });
});
