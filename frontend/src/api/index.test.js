import { describe, it, expect, vi, beforeEach } from 'vitest';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('./client', () => ({ default: { get } }));

import { patientApi, encounterApi } from './index';

describe('paginated list endpoints are unwrapped to arrays', () => {
  beforeEach(() => {
    get.mockReset();
  });

  it('patientApi.list unwraps PageResponse content', async () => {
    get.mockResolvedValue({ data: { content: [{ id: 'p1', name: 'Rahul' }], totalElements: 1 } });
    const { data } = await patientApi.list();
    expect(Array.isArray(data)).toBe(true);
    expect(data).toEqual([{ id: 'p1', name: 'Rahul' }]);
  });

  it('patientApi.list forwards search param and tolerates bare arrays', async () => {
    get.mockResolvedValue({ data: [{ id: 'p2' }] });
    await patientApi.list('anita');
    expect(get).toHaveBeenCalledWith('/patients', { params: { search: 'anita' } });
    const { data } = await patientApi.list();
    expect(data).toEqual([{ id: 'p2' }]);
  });

  it('patientApi.list yields empty array when content is missing', async () => {
    get.mockResolvedValue({ data: null });
    const { data } = await patientApi.list();
    expect(data).toEqual([]);
  });

  it('encounterApi.listByPatient unwraps PageResponse content', async () => {
    get.mockResolvedValue({ data: { content: [{ id: 'e1' }], totalElements: 4 } });
    const { data } = await encounterApi.listByPatient('pid');
    expect(data).toEqual([{ id: 'e1' }]);
  });
});

describe('page metadata survives unwrapping', () => {
  beforeEach(() => {
    get.mockReset();
  });

  // The lists are capped server-side at 20 rows, and the patient list and the
  // OPD picker were the only ways to reach a patient. Without the metadata a
  // list cannot show how many pages it has, and cannot tell whether Next is
  // still available — so unwrapPage used to throw all of it away.
  it('exposes page, size and totals alongside the content', async () => {
    get.mockResolvedValue({
      data: { content: [{ id: 'p1' }], page: 1, size: 20, totalElements: 23, totalPages: 2, first: false, last: true },
    });

    const { data, meta } = await patientApi.list(undefined, 1);

    expect(data).toEqual([{ id: 'p1' }]);
    expect(meta).toEqual({ page: 1, size: 20, totalElements: 23, totalPages: 2, first: false, last: true });
  });

  it('forwards the page param so a later page can be requested', async () => {
    get.mockResolvedValue({ data: { content: [], page: 2, size: 20, totalElements: 41, totalPages: 3, first: false, last: false } });

    await patientApi.list(undefined, 2);

    expect(get).toHaveBeenCalledWith('/patients', { params: { search: undefined, page: 2 } });
  });

  it('reports no meta for a bare array response, so no controls render', async () => {
    get.mockResolvedValue({ data: [{ id: 'p2' }] });

    const { data, meta } = await patientApi.list();

    expect(data).toEqual([{ id: 'p2' }]);
    expect(meta).toBeNull();
  });
});
