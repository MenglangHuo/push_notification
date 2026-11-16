import { apiClient } from './client'

export interface AvatarUploadResponse {
  /** Relative path for DB storage (e.g. "/avatars/uuid_ts.jpg") */
  path: string
  /** Full URL for display (e.g. "http://localhost:8080/uploads/avatars/uuid_ts.jpg") */
  url: string
}

export const filesApi = {
  /**
   * Upload an avatar image via multipart/form-data.
   * The backend validates type/size, resizes to max 512×512, and stores on disk.
   */
  async uploadAvatar(file: File): Promise<AvatarUploadResponse> {
    const formData = new FormData()
    formData.append('file', file)
    const res = await apiClient.post<AvatarUploadResponse>('/files/avatars', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return res.data
  },

  /**
   * Delete an avatar file from server storage.
   */
  async deleteAvatar(path: string): Promise<void> {
    await apiClient.delete('/files/avatars', { params: { path } })
  },
}
