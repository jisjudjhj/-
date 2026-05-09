import request from '../utils/request'

export function getProductCategories() {
  return request.get('/products/categories')
}

export function createProduct(payload) {
  return request.post('/products', payload)
}

export function updateProduct(id, payload) {
  return request.put(`/products/${id}`, payload)
}

export function deleteProduct(id) {
  return request.delete(`/products/${id}`)
}

export function updateProductStatus(id, status) {
  return request.put(`/products/${id}/status`, { status })
}
