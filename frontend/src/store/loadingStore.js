import { create } from 'zustand';

const useLoadingStore = create((set) => ({
  globalLoading: false,
  loadingCount: 0,
  startLoading: () => set((state) => ({ 
    loadingCount: state.loadingCount + 1, 
    globalLoading: true 
  })),
  stopLoading: () => set((state) => {
    const newCount = Math.max(0, state.loadingCount - 1);
    return { loadingCount: newCount, globalLoading: newCount > 0 };
  }),
  resetLoading: () => set({ loadingCount: 0, globalLoading: false }),
}));

export default useLoadingStore;