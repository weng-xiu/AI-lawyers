const axios = require('axios');

// 配置axios基础URL
const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000
});

// 测试用户端登录功能
async function testUserLogin() {
  try {
    console.log('测试用户端登录功能...');
    
    // 获取验证码
    console.log('获取验证码...');
    const captchaResponse = await api.get('/aiuser/captchaImage');
    console.log('验证码UUID:', captchaResponse.data.uuid);
    console.log('验证码启用状态:', captchaResponse.data.captchaEnabled);
    
    // 尝试登录testuser用户
    console.log('尝试登录testuser用户...');
    const loginResponse = await api.post('/aiuser/login', {
      username: 'testuser',
      password: '123456',
      code: '1234', // 如果验证码启用，这个值可能需要调整
      uuid: captchaResponse.data.uuid
    });
    
    console.log('登录响应:', loginResponse.data);
    
    if (loginResponse.data.code === 200) {
      console.log('登录成功！');
      
      // 获取用户信息
      const token = loginResponse.data.token;
      const userInfoResponse = await api.get('/aiuser/getInfo', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      console.log('用户信息:', userInfoResponse.data);
      
      // 获取路由信息
      const routerResponse = await api.get('/aiuser/getRouters', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      console.log('路由信息:', routerResponse.data);
      
      return;
    } else {
      console.log('登录失败:', loginResponse.data.msg);
      
      // 如果验证码启用，尝试不同的验证码
      if (captchaResponse.data.captchaEnabled) {
        console.log('验证码已启用，尝试不同的验证码值...');
        const possibleCodes = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '1234'];
        
        for (const code of possibleCodes) {
          try {
            // 每次尝试都重新获取验证码
            const newCaptchaResponse = await api.get('/aiuser/captchaImage');
            
            const response = await api.post('/aiuser/login', {
              username: 'testuser',
              password: '123456',
              code: code,
              uuid: newCaptchaResponse.data.uuid
            });
            
            if (response.data.code === 200) {
              console.log(`使用验证码 ${code} 登录成功！`);
              
              // 获取用户信息
              const token = response.data.token;
              const userInfoResponse = await api.get('/aiuser/getInfo', {
                headers: {
                  'Authorization': `Bearer ${token}`
                }
              });
              console.log('用户信息:', userInfoResponse.data);
              return;
            }
          } catch (error) {
            console.log(`验证码 ${code} 尝试失败:`, error.response ? error.response.data.msg : error.message);
          }
        }
        
        console.log('所有验证码尝试都失败');
      }
    }
  } catch (error) {
    console.error('测试用户端登录出错:', error.response ? error.response.data : error.message);
  }
}

// 执行测试
testUserLogin();