const axios = require('axios');

// 配置axios基础URL
const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 10000
});

// 测试登录功能
async function testLogin() {
  try {
    // 数学验证码通常是简单的加减法，我们尝试几个可能的答案
    const possibleAnswers = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
    
    // 登录 - 使用admin用户测试
    console.log('尝试登录...');
    
    for (const answer of possibleAnswers) {
      console.log(`尝试使用验证码答案: ${answer}`);
      
      // 每次尝试都重新获取验证码
      console.log('获取验证码...');
      const captchaResponse = await api.get('/captchaImage');
      console.log('验证码UUID:', captchaResponse.data.uuid);
      
      try {
        const loginResponse = await api.post('/login', {
          username: 'admin',
          password: 'admin123',
          code: answer,
          uuid: captchaResponse.data.uuid
        });
        
        console.log('登录响应:', loginResponse.data);
        
        if (loginResponse.data.code === 200) {
          console.log('登录成功！');
          
          // 获取用户信息
          const token = loginResponse.data.token;
          const userInfoResponse = await api.get('/getInfo', {
            headers: {
              'Authorization': `Bearer ${token}`
            }
          });
          console.log('用户信息:', userInfoResponse.data);
          return; // 登录成功，退出循环
        } else {
          console.log(`验证码 ${answer} 登录失败:`, loginResponse.data.msg);
        }
      } catch (error) {
        console.log(`验证码 ${answer} 请求出错:`, error.response ? error.response.data.msg : error.message);
      }
    }
    
    console.log('所有验证码尝试都失败了');
  } catch (error) {
    console.error('测试出错:', error.message);
    if (error.response) {
      console.error('响应数据:', error.response.data);
    }
  }
}

// 执行测试
testLogin();