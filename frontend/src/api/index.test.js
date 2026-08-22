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
