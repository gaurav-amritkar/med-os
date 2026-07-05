import client from './client';

export const authApi = {
  login: (credentials) => client.post('/auth/login', credentials),
  getMe: () => client.get('/users/me'),
};

export const patientApi = {
  list: (search) => client.get('/patients', { params: { search } }),
  get: (id) => client.get(`/patients/${id}`),
  getByUhid: (uhid) => client.get(`/patients/uhid/${uhid}`),
  register: (data) => client.post('/patients', data),
};

export const encounterApi = {
  create: (data) => client.post('/encounters', data),
  get: (id) => client.get(`/encounters/${id}`),
  listByPatient: (patientId) => client.get(`/encounters/patient/${patientId}`),
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
  dispense: (data) => client.post('/pharmacy/dispense', data),
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
  createInvoice: (data) => client.post('/billing/invoices', data),
  getInvoices: (patientId) => client.get(`/billing/patients/${patientId}/invoices`),
  getUnbilled: (patientId) => client.get(`/billing/patients/${patientId}/unbilled`),
  getInvoiceCharges: (invoiceId) => client.get(`/billing/invoices/${invoiceId}/charges`),
  recordPayment: (data) => client.post('/billing/payments', data),
  getPayments: (invoiceId) => client.get(`/billing/invoices/${invoiceId}/payments`),
};

export const dashboardApi = {
  dashboard: () => client.get('/dashboard'),
  notifications: (unreadOnly) => client.get('/notifications', { params: { unreadOnly } }),
  unreadCount: () => client.get('/notifications/unread-count'),
  markRead: (id) => client.put(`/notifications/${id}/read`),
};
