export const emptyPage = { number: 0, totalPages: 0, totalElements: 0 };

export function pageResult(data) {
  if (Array.isArray(data)) {
    return { content: data, page: { number: 0, totalPages: 1, totalElements: data.length } };
  }
  return {
    content: data?.content ?? [],
    page: {
      number: data?.number ?? 0,
      totalPages: data?.totalPages ?? 0,
      totalElements: data?.totalElements ?? 0,
    },
  };
}
