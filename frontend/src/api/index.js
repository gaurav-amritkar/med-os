import client from './client';
import { withIdempotencyKey } from './idempotency';

// Paginated list endpoints return PageResponse ({ content, ... }); the UI works with
// plain arrays, so unwrap here. Tolerates a bare array for backward compatibility.
const unwrapPage = ({ data }) => ({ data: Array.isArray(data) ? data : (data?.content ?? []) });

export const authApi = {
  login: (credentials) => client.post('/auth/login', credentials),
  getMe: () => client.get('/users/me'),
};

export const onboardingApi = {
  registerTenant: (data) => client.post('/onboarding/register', data),
};

export const patientApi = {
  list: (search) => client.get('/patients', { params: { search } }).then(unwrapPage),
  get: (id) => client.get(`/patients/${id}`),
  getByUhid: (uhid) => client.get(`/patients/uhid/${uhid}`),
  register: (data) => client.post('/patients', data),
};

export const encounterApi = {
  // Worklist: status filter, `mine` restricts to encounters this clinician
  // started. Without this there is no way back to an encounter you already
  // started, since the only other routes are by id and by patient.
  list: (params = {}) => client.get('/encounters', { params }).then(unwrapPage),
  create: (data) => client.post('/encounters', data),
  get: (id) => client.get(`/encounters/${id}`),
  listByPatient: (patientId) => client.get(`/encounters/patient/${patientId}`).then(unwrapPage),
  sign: (id) => client.post(`/encounters/${id}/sign`),
  suggestMedicines: (data) => client.post('/encounters/suggest-medicines', data),
  addPrescription: (id, data) => client.post(`/encounters/${id}/prescriptions`, data),
  listPrescriptions: (id) => client.get(`/encounters/${id}/prescriptions`),
  pendingPrescriptions: () => client.get('/encounters/prescriptions/pending'),
};

export const pharmacyApi = {
  listMedicines: () => client.get('/pharmacy/medicines'),
  getMedicine: (id) => client.get(`/pharmacy/medicines/${id}`),
  getBatches: (id) => client.get(`/pharmacy/medicines/${id}/batches`),
  addStock: (id, data) => client.post(`/pharmacy/medicines/${id}/stock-in`, null, { params: data }),
  // The backend requires Idempotency-Key here. Pass the same key to retry the
  // same dispense safely; omit it for a new operation.
  dispense: (data, idempotencyKey) =>
    client.post('/pharmacy/dispense', data, withIdempotencyKey({ idempotencyKey })),
  getTransactions: (medicineId) => client.get('/pharmacy/transactions', { params: { medicineId } }),
};

export const admissionApi = {
  admit: (data) => client.post('/admissions', data),
  discharge: (id, data) => client.put(`/admissions/${id}/discharge`, data),
  active: () => client.get('/admissions/active'),
  patientHistory: (patientId) => client.get(`/admissions/patient/${patientId}`),
  rooms: () => client.get('/admissions/rooms'),
  availableRooms: () => client.get('/admissions/rooms/available'),
};

export const billingApi = {
  // Both require Idempotency-Key; see pharmacyApi.dispense.
  createInvoice: (data, idempotencyKey) =>
    client.post('/billing/invoices', data, withIdempotencyKey({ idempotencyKey })),
  getInvoices: (patientId) => client.get(`/billing/patients/${patientId}/invoices`),
  getUnbilled: (patientId) => client.get(`/billing/patients/${patientId}/unbilled`),
  getInvoiceCharges: (invoiceId) => client.get(`/billing/invoices/${invoiceId}/charges`),
  recordPayment: (data, idempotencyKey) =>
    client.post('/billing/payments', data, withIdempotencyKey({ idempotencyKey })),
  getPayments: (invoiceId) => client.get(`/billing/invoices/${invoiceId}/payments`),
};

export const dashboardApi = {
  dashboard: () => client.get('/dashboard'),
  notifications: (unreadOnly) => client.get('/notifications', { params: { unreadOnly } }),
  unreadCount: () => client.get('/notifications/unread-count'),
  markRead: (id) => client.put(`/notifications/${id}/read`),
};
