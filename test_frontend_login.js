// 测试前端登录流程
const axios = require('axios');

// 配置axios
const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 30000
});

async function testLoginFlow() {
  try {
    console.log('1. 获取验证码...');
    const captchaResponse = await api.get('/captchaImage');
    console.log('验证码UUID:', captchaResponse.data.uuid);
    
    const { uuid } = captchaResponse.data;
    
    console.log('\n2. 尝试登录...');
    
    // 尝试不同的验证码答案
    const possibleAnswers = ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10'];
    let loginSuccess = false;
    let token = null;
    
    for (const answer of possibleAnswers) {
      try {
        console.log(`尝试验证码答案: ${answer}`);
        
        // 每次尝试前重新获取验证码
        const freshCaptchaResponse = await api.get('/captchaImage');
        const freshUuid = freshCaptchaResponse.data.uuid;
        
        const loginResponse = await api.post('/login', {
          username: 'admin',
          password: 'admin123',
          code: answer,
          uuid: freshUuid
        });
        
        if (loginResponse.data.code === 200) {
          console.log('登录成功!');
          token = loginResponse.data.token;
          loginSuccess = true;
          break;
        } else {
          console.log(`答案 ${answer} 失败: ${loginResponse.data.msg}`);
        }
      } catch (error) {
        console.log(`答案 ${answer} 请求出错:`, error.response ? error.response.data.msg : error.message);
      }
    }
    
    if (!loginSuccess) {
      console.log('\n所有验证码尝试都失败了');
      return;
    }
      console.log('\n3. 登录成功，获取token:', token);
      
      console.log('\n4. 使用token获取用户信息...');
      const userInfoResponse = await api.get('/getInfo', {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      });
      
      console.log('用户信息响应:', userInfoResponse.data);
      
      if (userInfoResponse.data.code === 200) {
        const user = userInfoResponse.data.user;
        console.log('\n5. 用户信息获取成功:');
        console.log('用户名:', user.userName);
        console.log('昵称:', user.nickName);
        console.log('用户类型:', user.userType);
        
        // 检查用户类型是否为普通用户(01)
        if (user.userType === '01') {
          console.log('\n6. 用户类型验证通过，可以访问用户端');
        } else {
          console.log('\n6. 用户类型不匹配，当前用户类型:', user.userType, '期望类型: 01');
        }
      } else {
        console.log('\n5. 用户信息获取失败:', userInfoResponse.data.msg);
      }
  } catch (error) {
    console.error('测试过程中出错:', error.response ? error.response.data : error.message);
  }
}

testLoginFlow();