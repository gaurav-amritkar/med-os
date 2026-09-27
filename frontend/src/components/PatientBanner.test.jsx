import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import PatientBanner from '../components/PatientBanner';
import usePatientBannerStore from '../store/patientBannerStore';

const PATIENT = {
  name: 'Anita Joshi',
  uhid: 'UHID000042',
  age: 28,
  sex: 'Female',
  bloodGroup: 'O+',
  dpdpConsent: true,
  acuity: 'critical',
  status: 'Critical',
  location: 'Ward 3B · Bed 12',
  allergy: 'Penicillin',
};

describe('PatientBanner', () => {
  beforeEach(() => {
    usePatientBannerStore.getState().clearPatient();
  });

  it('renders nothing when no patient is in context', () => {
    const { container } = render(<PatientBanner />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows identity, acuity, consent, location and allergy', () => {
    usePatientBannerStore.getState().setPatient(PATIENT);
    render(<PatientBanner />);

    expect(screen.getByText('Anita Joshi')).toBeInTheDocument();
    expect(screen.getByText('UHID000042')).toBeInTheDocument();
    expect(screen.getByText('28 y')).toBeInTheDocument();
    expect(screen.getByText('Female')).toBeInTheDocument();
    expect(screen.getByText('O+')).toBeInTheDocument();
    expect(screen.getByText('Critical')).toBeInTheDocument();
    expect(screen.getByText('DPDP consent')).toBeInTheDocument();
    expect(screen.getByText(/Ward 3B/)).toBeInTheDocument();
    expect(screen.getByText(/Penicillin/)).toBeInTheDocument();
  });

  it('labels the banner for assistive technology', () => {
    usePatientBannerStore.getState().setPatient(PATIENT);
    render(<PatientBanner />);
    expect(
      screen.getByRole('region', { name: 'Patient in context' })
    ).toBeInTheDocument();
  });

  it('surfaces absent consent as a critical tag rather than silence', () => {
    usePatientBannerStore
      .getState()
      .setPatient({ ...PATIENT, dpdpConsent: false });
    render(<PatientBanner />);
    expect(screen.getByText('No consent')).toBeInTheDocument();
  });

  it('falls back to a not-admitted location rather than an empty cell', () => {
    usePatientBannerStore.getState().setPatient({ ...PATIENT, location: undefined });
    render(<PatientBanner />);
    expect(screen.getByText('Not admitted')).toBeInTheDocument();
  });

  it('defaults an unrecognised acuity to normal rather than rendering a broken modifier', () => {
    usePatientBannerStore
      .getState()
      .setPatient({ ...PATIENT, acuity: 'disastrous' });
    const { container } = render(<PatientBanner />);
    expect(container.querySelector('.banner--normal')).toBeInTheDocument();
    expect(container.querySelector('.banner--disastrous')).toBeNull();
  });
});
