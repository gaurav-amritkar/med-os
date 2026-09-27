import { create } from 'zustand';

/**
 * The patient currently in context, rendered by PatientBanner across route
 * changes. Pages call setPatient on selection and clearPatient on unmount.
 *
 * Shape (all optional except name):
 *   { name, uhid, age, sex, bloodGroup, dpdpConsent,
 *     acuity: 'critical' | 'urgent' | 'normal', status, location, allergy }
 */
const usePatientBannerStore = create((set) => ({
  patient: null,
  setPatient: (patient) => set({ patient }),
  clearPatient: () => set({ patient: null }),
}));

export default usePatientBannerStore;
