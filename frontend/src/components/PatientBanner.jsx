import usePatientBannerStore from '../store/patientBannerStore';
import Icon from './icons';

const ACUITY = new Set(['critical', 'urgent', 'normal']);

/**
 * Persistent patient identity band.
 *
 * A clinician should never act on a patient they cannot identify, so identity,
 * acuity, consent and allergies stay pinned above every patient-context screen.
 * Renders nothing when no patient is in context, which lets Layout mount it
 * unconditionally.
 *
 * The pattern comes from the NHS Common User Interface standards (ISB 1505,
 * Patient Banner), now deprecated as a standard but still the correct shape.
 */
export default function PatientBanner() {
  const patient = usePatientBannerStore((s) => s.patient);
  if (!patient) return null;

  const acuity = ACUITY.has(patient.acuity) ? patient.acuity : 'normal';

  return (
    <section
      className={`banner banner--${acuity}`}
      aria-label="Patient in context"
    >
      <div className="banner__top">
        <h2 className="banner__name">{patient.name}</h2>

        {patient.status && (
          <span className={`tag tag--${acuity}`}>
            <span className="tag__dot" />
            {patient.status}
          </span>
        )}

        <p className="banner__meta">
          {patient.uhid && <span className="uid">{patient.uhid}</span>}
          {patient.age != null && <span>{patient.age} y</span>}
          {patient.sex && <span>{patient.sex}</span>}
          {patient.bloodGroup && <span>{patient.bloodGroup}</span>}
          {patient.dpdpConsent === true && (
            <span className="tag tag--normal tag--sq">DPDP consent</span>
          )}
          {patient.dpdpConsent === false && (
            <span className="tag tag--critical tag--sq">No consent</span>
          )}
        </p>
      </div>

      <div className="banner__foot">
        <p className="banner__loc">
          <Icon name="admissions" size={16} />
          {patient.location || 'Not admitted'}
        </p>
        {patient.allergy && (
          <p className="banner__allergy">
            <Icon name="alert" size={16} />
            Allergy: {patient.allergy}
          </p>
        )}
      </div>
    </section>
  );
}
