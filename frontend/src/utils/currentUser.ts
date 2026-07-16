export const DEFAULT_USER_ID = 'FCD122540D98DF1D1D8D449F3CD132B6'
export const CURRENT_USER_KEY = 'code-review-current-user-id'

export function currentUserId() {
  return localStorage.getItem(CURRENT_USER_KEY) || DEFAULT_USER_ID
}
