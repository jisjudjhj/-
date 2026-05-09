import request from '../utils/request'

export function uploadImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/upload/image', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export function uploadImages(files) {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  return request.post('/upload/images', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}
