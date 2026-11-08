// src/api/index.js
import axios from 'axios'

const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api'

const http = axios.create({ baseURL: BASE, timeout: 200000 }) // 非流式对话本地模型较慢，超时放宽

http.interceptors.request.use((cfg) => {
  const t = localStorage.getItem('token')
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

function logout() {
  localStorage.removeItem('token')
  window.location.href = '/login'
}

http.interceptors.response.use(
  (resp) => resp.data.data,                       // 后端统一返回 {code,msg,data}
  (err) => {
    if (err.response?.status === 401) logout()
    return Promise.reject(new Error(err.response?.data?.msg || '网络错误'))
  },
)

// ---- 账号 ----
export const sendSmsCode = (phone) => http.post('/auth/sms/send', { phone })
export const register = (phone, password, code) => http.post('/auth/register', { phone, password, code })
export const login = (phone, password) => http.post('/auth/login', { phone, password })
//   成功返回 {token, userId, assessmentDone}；登录后把 token 存 localStorage('token')

// ---- 测评 ----
export const getQuestions = () => http.get('/assessment/questions')
export const submitAssessment = (answers) => http.post('/assessment/submit', { answers })
export const assessmentStatus = () => http.get('/assessment/status')

// ---- 画像 ----
export const getProfile = () => http.get('/profile')   // summaryStatus === 'PENDING' 时可 3 秒轮询
export const deleteAdvice = (id) => http.delete(`/profile/advices/${id}`)   // id 为数字（advice_record.id）
export const downloadProfileUrl = `${BASE}/profile/download` // 需带 token，建议用 axios blob 下载

// ---- 对话 ----
export const chatOnce = (message, sessionId) => http.post('/chat', { message, sessionId })
export const chatHistory = (sessionId) => http.get('/chat/history', { params: { sessionId } })
export const chatSessions = () => http.get('/chat/sessions')                          // 会话列表
export const renameChatSession = (sid, title) => http.put(`/chat/sessions/${sid}/title`, { title })
export const deleteChatSession = (sid) => http.delete(`/chat/sessions/${sid}`)

/** 流式对话（SSE over fetch）：onMeta({sessionId}) / onToken(text) / onDone() / onError(msg) */
export async function chatStream(message, sessionId, { onMeta, onToken, onDone, onError, signal } = {}) {
  const resp = await fetch(`${BASE}/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('token')}`,
    },
    body: JSON.stringify({ message, sessionId }),
    signal,
  })
  if (resp.status === 401) return logout()
  if (!resp.ok) {
    let msg = '请求失败'
    try { msg = (await resp.json()).msg || msg } catch (_) { /* ignore */ }
    return onError?.(msg)
  }
  const reader = resp.body.getReader()
  const dec = new TextDecoder()
  let buf = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buf += dec.decode(value, { stream: true })
    let idx
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const block = buf.slice(0, idx)
      buf = buf.slice(idx + 2)
      let ev = 'message'
      let data = ''
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) ev = line.slice(6).trim()
        else if (line.startsWith('data:')) data += line.slice(5).trim()
      }
      if (!data) continue
      const obj = JSON.parse(data)
      if (ev === 'meta') onMeta?.(obj)
      else if (ev === 'token') onToken?.(obj.t)
      else if (ev === 'done') onDone?.()
      else if (ev === 'error') onError?.(obj.msg)
    }
  }
}
