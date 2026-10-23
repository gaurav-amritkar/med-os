import client from './client';
import { withIdempotencyKey } from './idempotency';

// Paginated list endpoints return PageResponse ({ content, ... }); the UI works with
// plain arrays, so unwrap here. Tolerates a bare array for backward compatibility.
// The page metadata is kept alongside as `meta` — without it a list cannot show
// how many pages it has, or stop offering a Next button on the last one.
const unwrapPage = ({ data }) => {
  if (Array.isArray(data)) return { data, meta: null };
  return {
    data: data?.content ?? [],
    meta: {
      page: data?.page ?? 0,
      size: data?.size ?? 0,
      totalElements: data?.totalElements ?? 0,
      totalPages: data?.totalPages ?? 0,
      first: data?.first ?? true,
      last: data?.last ?? true,
    },
  };
};

export const authApi = {
  login: (credentials) => client.post('/auth/login', credentials),
  getMe: () => client.get('/users/me'),
};

/**
 * Staff management. ADMIN-only on the server; the UI gates the route as well so a
 * non-admin is not offered a page that would only ever return 403.
 */
export const userApi = {
  list: () => client.get('/users'),
  create: (data) => client.post('/users', data),
  setRole: (userId, role) => client.put(`/users/${userId}/role`, { role }),
  setActive: (userId, active) => client.put(`/users/${userId}/active`, { active }),
  setPermissions: (userId, permissions) => client.put(`/users/${userId}/permissions`, { permissions }),
};

export const onboardingApi = {
  registerTenant: (data) => client.post('/onboarding/register', data),
  // Platform-only (SUPER_ADMIN). 403s for anyone else, which the page treats
  // as "not available here" rather than as an error worth shouting about.
  listTenants: () => client.get('/tenants'),
};

export const patientApi = {
  // Hub options object, not positional args: a 4-parameter positional list
  // (search, type, bloodGroup, page) reads as four `undefined`s and one real
  // value, and a transposed call is misaligned silently. an object literal
  // names what is being sent. `page` is sent only when asked for, so the
  // common first-page call is unchanged; a search resets to page 0 because
  // results are a different set.
  list: ({ search, type, bloodGroup, page } = {}) =>
    client.get('/patients', { params: { search, type, bloodGroup, page } }).then(unwrapPage),
  summary: () => client.get('/patients/summary'),
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
  // The backend has accepted this since the catalogue endpoint was written;
  // nothing in the UI called it, so a pharmacist could not create the very
  // first medicine stock-in depends on.
  addMedicine: (data) => client.post('/pharmacy/medicines', data),
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

/**
 * The Enquiry Desk (Wave 1): front-desk pre-patient contacts. Writes are
 * front-desk authority (receptionist/admin) and the board is readable by the
 * clinical roles, so the route gate mirrors that.
 */
export const enquiryApi = {
  list: (status, type, page) =>
    client.get('/enquiries', { params: { status, type, page } }).then(unwrapPage),
  summary: () => client.get('/enquiries/summary'),
  create: (data) => client.post('/enquiries', data),
  updateStatus: (id, data) => client.post(`/enquiries/${id}/status`, data),
};

export const dashboardApi = {
  dashboard: () => client.get('/dashboard'),
  notifications: (unreadOnly) => client.get('/notifications', { params: { unreadOnly } }),
  unreadCount: () => client.get('/notifications/unread-count'),
  markRead: (id) => client.put(`/notifications/${id}/read`),
};

/**
 * The Appointments board (Wave 1): per-tenant appointments with per-day tokens.
 */
export const appointmentApi = {
  list: (status, doctorId, type, page) =>
    client.get('/appointments', { params: { status, doctorId, type, page } }).then(unwrapPage),
  summary: () => client.get('/appointments/summary'),
  book: (data) => client.post('/appointments', data),
  checkIn: (id) => client.post(`/appointments/${id}/check-in`),
  updateStatus: (id, data) => client.post(`/appointments/${id}/status`, data),
  writeNote: (id, note) => client.post(`/appointments/${id}/note`, note),
  delete: (id) => client.delete(`/appointments/${id}`),
  queue: (status, page) => client.get('/appointments/queue', { params: { status, page } }).then(unwrapPage),
};
