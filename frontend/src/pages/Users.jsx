import { useEffect, useState } from 'react';
import { userApi } from '../api';
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

const emptyForm = {
  username: '',
  fullName: '',
  role: 'doctor',
  password: '',
  email: '',
  specialization: '',
};

export default function Users() {
  const [staff, setStaff] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showAdd, setShowAdd] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [errors, setErrors] = useState({});
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
      await userApi.create(form);
      addToast(`${form.fullName} added as ${form.role}`, 'success');
      setShowAdd(false);
      setForm(emptyForm);
      setErrors({});
      load();
    } catch (err) {
      // Field-level messages come back keyed by field name, so they land on the control
      // they belong to rather than only in a toast.
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
      load();
    }
  };

  const toggleActive = async (user) => {
    try {
      await userApi.setActive(user.id, !user.active);
      addToast(
        user.active ? `${user.fullName}'s access ended` : `${user.fullName}'s access restored`,
        'success'
      );
      load();
    } catch (err) {
      addToast(err.response?.data?.message || 'Could not change access', 'critical');
      load();
    }
  };

  const activeCount = staff.filter((s) => s.active).length;

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Staff</h1>
          <p>
            Who can sign in to this hospital. {activeCount} active of {staff.length}.
          </p>
        </div>
        <button className="btn-primary" onClick={() => setShowAdd(true)}>
          + Add Staff
        </button>
      </div>

      <div className="card">
        <table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Username</th>
              <th>Role</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr>
                <td colSpan={5} className="staff-meta">
                  Loading staff…
                </td>
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
                </td>
                <td className="staff-actions">
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
        <div className="modal-overlay" onClick={() => setShowAdd(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h2>Add Staff</h2>
              <button className="btn-ghost btn-sm" onClick={() => setShowAdd(false)}>
                ✕
              </button>
            </div>
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
              <div className="form-actions">
                <button type="button" className="btn-ghost" onClick={() => setShowAdd(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn-primary">
                  Add Staff
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * A labelled input that shows its own error.
 *
 * <p>The message states the rule, and `aria-invalid`/`aria-describedby` wire it to the
 * control so it is announced rather than only coloured.
 */
function Field({ id, label, value, onChange, error, hint, type = 'text' }) {
  const describedBy = [error ? `${id}-error` : null, hint ? `${id}-hint` : null]
    .filter(Boolean)
    .join(' ');
  return (
    <div className="form-group">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={describedBy || undefined}
      />
      {hint && !error && (
        <div id={`${id}-hint`} className="field-hint">
          {hint}
        </div>
      )}
      {error && (
        <div id={`${id}-error`} className="field-error" role="alert">
          {error}
        </div>
      )}
    </div>
  );
}
