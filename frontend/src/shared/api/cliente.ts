import axios from 'axios'

export const cliente = axios.create({
  baseURL: '/api/v1',
})
