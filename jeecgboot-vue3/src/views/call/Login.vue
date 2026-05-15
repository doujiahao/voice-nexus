<template>
  <div class="login-page">
    <div class="login-bg login-bg--top"></div>
    <div class="login-bg login-bg--bottom"></div>

    <main class="login-shell">
      <section class="login-card">
        <div class="login-brand">
          <img class="login-brand__bg-img" src="" alt="" />
          <div class="login-brand__content">
            <img class="login-brand__logo-img" :src="logoImg" alt="logo" />
            <div class="login-brand__title">智能话务客服平台</div>
          </div>
        </div>

        <section class="login-form-panel">
          <div class="login-form">
            <label class="login-field" :class="{ 'is-focused': focusField === 'username' }">
              <span class="login-field__icon">👤</span>
              <input
                v-model="form.username"
                type="text"
                placeholder="请输入账号"
                autocomplete="username"
                @focus="focusField = 'username'"
                @blur="focusField = ''"
                @keyup.enter="handleLogin"
              />
            </label>

            <label class="login-field" :class="{ 'is-focused': focusField === 'password' }">
              <span class="login-field__icon">🔒</span>
              <input
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="密码"
                autocomplete="current-password"
                @focus="focusField = 'password'"
                @blur="focusField = ''"
                @keyup.enter="handleLogin"
              />
              <button type="button" class="login-field__suffix" @click="showPassword = !showPassword">
                {{ showPassword ? '隐藏' : '显示' }}
              </button>
            </label>

            <!-- 验证码暂时屏蔽 -->
            <!-- <div class="login-captcha-row">
              <label class="login-field login-field--captcha" :class="{ 'is-focused': focusField === 'captcha' }">
                <span class="login-field__icon">🛡️</span>
                <input
                  v-model="form.captcha"
                  type="text"
                  placeholder="验证码"
                  autocomplete="one-time-code"
                  maxlength="4"
                  @focus="focusField = 'captcha'"
                  @blur="focusField = ''"
                  @keyup.enter="handleLogin"
                />
              </label>
              <button type="button" class="login-captcha" @click="refreshCaptcha" title="点击刷新验证码">
                <img v-if="captchaImage" :src="captchaImage" class="login-captcha__img" alt="验证码" />
                <span v-else class="login-captcha__pattern">加载中</span>
              </button>
            </div> -->

            <div v-if="errorMsg" class="login-error">{{ errorMsg }}</div>

            <button type="button" class="login-submit" :disabled="loading" @click="handleLogin">
              {{ loading ? '登录中...' : '登录' }}
            </button>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '/@/store/modules/user'
import { usePermissionStore } from '/@/store/modules/permission'
import { PAGE_NOT_FOUND_ROUTE } from '/@/router/routes/basic'
import { getCodeInfo } from '/@/api/sys/user'
import { encryptAESCBC } from '/@/utils/cipher'
import logoImg from './assets/logo.png'

const router          = useRouter()
const userStore       = useUserStore()
const permissionStore = usePermissionStore()

const form = reactive({ username: '', password: '', captcha: '' })
const showPassword = ref(false)
const focusField   = ref('')
const loading      = ref(false)
const errorMsg     = ref('')
const captchaImage = ref('')
const checkKey     = ref('')

async function refreshCaptcha(): Promise<void> {
  checkKey.value = String(Date.now()) + Math.random().toString(36).slice(-4)
  try {
    captchaImage.value = await getCodeInfo(checkKey.value)
  } catch {
    captchaImage.value = ''
  }
}

async function handleLogin(): Promise<void> {
  errorMsg.value = ''
  if (!form.username.trim()) { errorMsg.value = '请输入账号'; return }
  if (!form.password)        { errorMsg.value = '请输入密码'; return }
  // 验证码暂时屏蔽：不再校验

  console.log('[Login] handleLogin 执行，即将调用 loginApi')
  loading.value = true
  try {
    await userStore.login({
      username:     form.username,
      password:     encryptAESCBC(form.password),
      captcha:      '',
      checkKey:     '',
      loginOrgCode: '',
      mode:         'none',
      goHome:       false,
    })

    console.log('[Login] 登录成功，isDynamicAddedRoute:', permissionStore.getIsDynamicAddedRoute)
    if (!permissionStore.getIsDynamicAddedRoute) {
      const routes = await permissionStore.buildRoutesAction()
      console.log('[Login] 构建了', routes.length, '条路由')
      routes.forEach((route) => router.addRoute(route as any))
      router.addRoute(PAGE_NOT_FOUND_ROUTE as any)
      permissionStore.setDynamicAddedRoute(true)
    }

    console.log('[Login] 即将跳转 /call/workspace')
    await router.replace('/call/workspace')
    console.log('[Login] 跳转完成，当前路由:', router.currentRoute.value.path)
  } catch (err: any) {
    errorMsg.value = err?.message || '登录失败，请检查账号密码或验证码'
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => refreshCaptcha())
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: radial-gradient(circle at top, rgba(255,255,255,0.95), rgba(232,241,252,0.95) 35%, rgba(223,232,245,1) 100%);
  position: relative;
}
.login-bg { position: absolute; inset: auto; border-radius: 999px; filter: blur(60px); pointer-events: none; }
.login-bg--top    { top: -120px; left: 10%; width: 280px; height: 280px; background: rgba(59,130,246,0.12); }
.login-bg--bottom { right: 8%; bottom: -160px; width: 360px; height: 360px; background: rgba(37,99,235,0.1); }
.login-shell { width: min(940px, calc(100vw - 48px)); position: relative; z-index: 1; }
.login-card { display: grid; grid-template-columns: minmax(0,1.02fr) minmax(0,0.98fr); min-height: 600px; border-radius: 6px; overflow: hidden; background: #fff; box-shadow: 0 18px 48px rgba(79,102,144,0.15); }
.login-brand { position: relative; padding: 56px 42px; background: #0b1e3c; color: #fff; display: flex; align-items: center; justify-content: center; min-height: 100%; }
.login-brand__bg-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; z-index: 0; }
.login-brand__bg-img[src=""] { display: none; }
.login-brand__content { position: relative; z-index: 1; text-align: center; }
.login-brand__logo-img { display: block; max-width: 180px; height: auto; object-fit: contain; margin: 0 auto 20px; }
.login-brand__title { font-size: 45px; font-weight: 800; line-height: 1.2; letter-spacing: 2px; color: #fff; }
.login-form-panel { background: #fff; display: flex; align-items: center; justify-content: center; padding: 48px 46px; }
.login-form { width: min(350px, 100%); }
.login-field { height: 46px; display: flex; align-items: center; gap: 10px; border: 1px solid #e5e7eb; border-radius: 3px; padding: 0 14px; margin-bottom: 24px; background: #fff; transition: border-color 0.18s ease, box-shadow 0.18s ease; }
.login-field.is-focused { border-color: #6aa1ff; box-shadow: 0 0 0 3px rgba(106,161,255,0.12); }
.login-field__icon { width: 18px; display: inline-flex; justify-content: center; font-size: 16px; opacity: 0.68; flex-shrink: 0; }
.login-field input { flex: 1; border: 0; outline: none; background: transparent; font-size: 14px; color: #1f2937; }
.login-field input::placeholder { color: #a3aab5; }
.login-field__suffix { border: 0; background: transparent; color: #7c8796; font-size: 12px; cursor: pointer; padding: 0; flex-shrink: 0; }
.login-captcha-row { display: grid; grid-template-columns: minmax(0,1fr) 118px; gap: 10px; align-items: center; margin-bottom: 24px; }
.login-field--captcha { margin-bottom: 0; }
.login-captcha { height: 46px; border: 1px solid #d7c0ea; border-radius: 3px; background: linear-gradient(135deg,rgba(140,91,194,0.45),rgba(161,110,220,0.55)); cursor: pointer; padding: 0; overflow: hidden; }
.login-captcha__img { width: 100%; height: 100%; object-fit: cover; display: block; }
.login-captcha__pattern { display: inline-flex; align-items: center; justify-content: center; width: 100%; height: 100%; font-size: 12px; color: #fff; }
.login-error { color: #ef4444; font-size: 13px; margin-bottom: 12px; text-align: center; }
.login-submit { width: 100%; height: 48px; border: 0; border-radius: 4px; background: linear-gradient(180deg,#6aa1ff 0%,#5b96f6 100%); color: #fff; font-size: 16px; font-weight: 700; letter-spacing: 1px; cursor: pointer; box-shadow: 0 10px 18px rgba(90,140,224,0.22); transition: filter 0.15s; }
.login-submit:hover:not(:disabled) { filter: brightness(1.05); }
.login-submit:active:not(:disabled) { transform: translateY(1px); }
.login-submit:disabled { opacity: 0.7; cursor: not-allowed; }
@media (max-width: 900px) {
  .login-shell { width: min(560px, calc(100vw - 32px)); }
  .login-card { grid-template-columns: 1fr; }
  .login-brand { min-height: 300px; padding: 36px 28px; }
  .login-form-panel { padding: 36px 28px 40px; }
}
@media (max-width: 560px) {
  .login-shell { width: calc(100vw - 20px); }
  .login-brand__title { font-size: 28px; }
  .login-captcha-row { grid-template-columns: 1fr; }
}
</style>
