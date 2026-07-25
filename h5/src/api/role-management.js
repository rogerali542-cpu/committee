import core from '@/api/core'

export default {
  secretaryList() {
    return core.request('GET', '/api/role-management/secretaries')
  },
  authorizeSecretary(id) {
    return core.request('POST', '/api/role-management/secretaries/' + id + '/authorize')
  },
  revokeSecretary(id) {
    return core.request('POST', '/api/role-management/secretaries/' + id + '/revoke')
  },
  managementOverview() {
    return core.request('GET', '/api/management/overview')
  }
}
