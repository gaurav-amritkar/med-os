import { useEffect, useState } from 'react';
import { userApi } from '../api';
import Modal from '../components/Modal';
import useToastStore from '../store/toastStore';

/**
 * Staff management for this hospital.
 *
 * <p>A tenant could only ever have the one admin made at onboarding, so a new hospital had
 * nobody to log in as but its owner. This is where the doctor, nurse and receptionist get
 * added.
 *
 * <p>Two things the UI has to make obvious, because both are easy to get wrong silently:
 * the temporary password is a shared secret until its owner changes it, and access is
 * per-hospital, so ending it here does not end it at a clinic the person also works at.
 */

const ROLES = [
  { value: 'admin', label: 'Admin' },
  { value: 'doctor', label: 'Doctor' },
  { value: 'nurse', label: 'Nurse' },
  { value: 'receptionist', label: 'Receptionist' },
  { value: 'pharmacist', label: 'Pharmacist' },
  { value: 'billing', label: 'Billing' },
];

const PERMISSION_OPTIONS = [
  { value: '/patients', label: 'Patients' },
  { value: '/enquiries', label: 'Enquiry Desk' },
  { value: '/appointments', label: 'Appointments' },
  { value: '/appointments/queue', label: 'Token Queue' },
  { value: '/encounters', label: 'OPD / Encounters' },
  { value: '/admissions', label: 'IPD / Wards' },
  { value: '/pharmacy', label: 'Pharmacy' },
  { value: '/billing', label: 'Billing' },
];

const emptyForm = {
  username: '',
  fullName: '',
  role: 'doctor',
  password: '',
  email: '',
  specialization: '',
  permissions: [],
};

export default function Users() {
  const [staff, setStaff] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
  const [editingUser, setEditingUser] = useState(null);
  const [editPerms, setEditPerms] = useState([]);
  const addToast = useToastStore((s) => s.addToast);

  const load = async () => {
    try {
      const { data } = await userApi.list();
      setStaff(data || []);
    } catch {
      addToast('Could not load staff', 'critical');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const validate = () => {
    const next = {};
    if (!form.username.trim()) next.username = 'Required';
    else if (form.username.trim().length < 3) next.username = 'At least 3 characters';
    if (!form.fullName.trim()) next.fullName = 'Required';
    if (!form.password) next.password = 'Required';
    else if (form.password.length < 8) next.password = 'At least 8 characters';
    if (form.email && !/^\S+@\S+\.\S+$/.test(form.email)) next.email = 'Enter a valid email';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const submit = async (e) => {
    e.preventDefault();
    if (!validate()) return;
    try {
      const payload = {
        ...form,
        permissions: form.permissions.join(','),
      };
      await userApi.create(payload);
      addToast(`${form.fullName} added as ${form.role}`, 'success');
      setShowAdd(false);
      setForm(emptyForm);
      setErrors({});
      load();
    } catch (err) {
      const fieldErrors = err.response?.data?.validationErrors;
      if (fieldErrors && Object.keys(fieldErrors).length) {
        setErrors(fieldErrors);
      }
      addToast(err.response?.data?.message || 'Could not add this person', 'critical');
    }
  };

  const changeRole = async (user, role) => {
    try {
      await userApi.setRole(user.id, role);
      addToast(`${user.fullName} is now ${role}`, 'success');
      load();
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not change the role', 'critical');
    }
  };

  const toggleActive = async (user) => {
    const nextActive = !user.active;
    try {
      await userApi.setActive(user.id, nextActive);
      addToast(`${user.fullName} access ${nextActive ? 'restored' : 'ended'}`, 'success');
      load();
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not change access', 'critical');
    }
  };

  const openPermissionsModal = (user) => {
    setEditingUser(user);
    const existing = user.permissions ? user.permissions.split(',').map((p) => p.trim()).filter(Boolean) : [];
    setEditPerms(existing);
  };

  const savePermissions = async () => {
    if (!editingUser) return;
    try {
      const permStr = editPerms.join(',');
      await userApi.setPermissions(editingUser.id, permStr);
      addToast(`Permissions updated for ${editingUser.fullName}`, 'success');
      setEditingUser(null);
      load();
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not update permissions', 'critical');
    }
  };

  const toggleFormPerm = (val) => {
    setForm((f) => ({
      ...f,
      permissions: f.permissions.includes(val)
        ? f.permissions.filter((p) => p !== val)
        : [...f.permissions, val],
    }));
  };

  const toggleEditPerm = (val) => {
    setEditPerms((eps) =>
      eps.includes(val) ? eps.filter((p) => p !== val) : [...eps, val]
    );
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Staff Management</h1>
          <p className="page-sub">Add doctors, nurses, receptionists and other staff, and manage their permissions.</p>
        </div>
        <button className="btn-primary" onClick={() => setShowAdd(true)}>
          + Add Staff
        </button>
      </div>

      <div className="card">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Username</th>
              <th>Role</th>
              <th>Status &amp; Permissions</th>
              <th className="text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={5} className="text-center">Loading staff...</td>
              </tr>
            )}
            {!loading && staff.length === 0 && (
              <tr>
                <td colSpan={5}>
                  <div className="empty-state">
                    <p>No staff yet. Add the doctor, nurse or receptionist who works here.</p>
                  </div>
                </td>
              </tr>
            )}
            {staff.map((s) => (
              <tr key={s.id} className={s.active ? undefined : 'staff-row-inactive'}>
                <td>
                  <div className="staff-name">{s.fullName}</div>
                  {s.email && <div className="staff-meta">{s.email}</div>}
                </td>
                <td className="staff-username">{s.username}</td>
                <td>
                  <select
                    value={s.role || ''}
                    onChange={(e) => changeRole(s, e.target.value)}
                    aria-label={`Role for ${s.fullName}`}
                    className="staff-role"
                  >
                    {ROLES.map((r) => (
                      <option key={r.value} value={r.value}>
                        {r.label}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  {!s.active && <span className="badge badge-danger">No access</span>}
                  {s.active && s.mustChangePassword && (
                    <span className="badge badge-warning" title="Still using the password an admin set">
                      Must change password
                    </span>
                  )}
                  {s.active && !s.mustChangePassword && (
                    <span className="badge badge-success">Active</span>
                  )}
                  {s.permissions && (
                    <div style={{ fontSize: '0.75rem', color: 'var(--ink-muted)', marginTop: 4 }}>
                      Perms: {s.permissions}
                    </div>
                  )}
                </td>
                <td className="staff-actions" style={{ textAlign: 'right' }}>
                  <button
                    className="btn-ghost btn-sm"
                    onClick={() => openPermissionsModal(s)}
                    style={{ marginRight: 8 }}
                  >
                    Permissions
                  </button>
                  <button
                    className={s.active ? 'btn-ghost btn-sm' : 'btn-primary btn-sm'}
                    onClick={() => toggleActive(s)}
                  >
                    {s.active ? 'End access' : 'Restore access'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showAdd && (
        <Modal title="Add Staff" onClose={() => setShowAdd(false)}>
            <p className="staff-note">
              They sign in with this password once, then choose their own.
            </p>
            <form onSubmit={submit}>
              <Field
                id="fullName"
                label="Full Name *"
                error={errors.fullName}
                value={form.fullName}
                onChange={(v) => setForm({ ...form, fullName: v })}
              />
              <Field
                id="username"
                label="Username *"
                error={errors.username}
                hint="Letters, digits, dot, underscore or hyphen"
                value={form.username}
                onChange={(v) => setForm({ ...form, username: v })}
              />
              <div className="form-group">
                <label htmlFor="role">Role *</label>
                <select
                  id="role"
                  value={form.role}
                  onChange={(e) => setForm({ ...form, role: e.target.value })}
                  style={{ width: '100%', padding: '10px 12px', borderRadius: 'var(--r-sm)', border: '1px solid var(--line)' }}
                >
                  {ROLES.map((r) => (
                    <option key={r.value} value={r.value}>
                      {r.label}
                    </option>
                  ))}
                </select>
              </div>
              <Field
                id="password"
                label="Temporary Password *"
                type="password"
                error={errors.password}
                hint="At least 8 characters. They must change it after signing in."
                value={form.password}
                onChange={(v) => setForm({ ...form, password: v })}
              />
              <Field
                id="email"
                label="Email"
                type="email"
                error={errors.email}
                value={form.email}
                onChange={(v) => setForm({ ...form, email: v })}
              />
              <Field
                id="specialization"
                label="Specialization"
                value={form.specialization}
                onChange={(v) => setForm({ ...form, specialization: v })}
              />

              <div className="form-group" style={{ marginTop: 16 }}>
                <label style={{ fontWeight: 600, display: 'block', marginBottom: 8 }}>Additional Permissions / Options</label>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                  {PERMISSION_OPTIONS.map((po) => (
                    <label key={po.value} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.85rem', cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={form.permissions.includes(po.value)}
                        onChange={() => toggleFormPerm(po.value)}
                      />
                      {po.label}
                    </label>
                  ))}
                </div>
              </div>

              <div className="form-actions" style={{ marginTop: 20 }}>
                <button type="button" className="btn-ghost" onClick={() => setShowAdd(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn-primary">
                  Add Staff
                </button>
              </div>
            </form>
        </Modal>
      )}

      {editingUser && (
        <Modal title={`Permissions for ${editingUser.fullName}`} onClose={() => setEditingUser(null)} size="narrow">
            <p className="staff-note">
              Grant specific module or option access beyond their base role ({editingUser.role}).
            </p>
            <div style={{ margin: '20px 0', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              {PERMISSION_OPTIONS.map((po) => (
                <label key={po.value} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '0.9rem', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={editPerms.includes(po.value)}
                    onChange={() => toggleEditPerm(po.value)}
                  />
                  {po.label}
                </label>
              ))}
            </div>
            <div className="form-actions">
              <button type="button" className="btn-ghost" onClick={() => setEditingUser(null)}>
                Cancel
              </button>
              <button type="button" className="btn-primary" onClick={savePermissions}>
                Save Permissions
              </button>
            </div>
        </Modal>
      )}
    </div>
  );
}

function Field({ id, label, value, onChange, error, hint, type = 'text' }) {
  const describedBy = [error ? `${id}-error` : null, hint ? `${id}-hint` : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className="form-group">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        className={error ? 'input-error' : undefined}
        aria-invalid={!!error}
        aria-describedby={describedBy}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {hint && !error && <span id={`${id}-hint`} className="form-hint">{hint}</span>}
      {error && <span id={`${id}-error`} className="field-error">{error}</span>}
    </div>
  );
}
