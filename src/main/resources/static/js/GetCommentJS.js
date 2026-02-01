// 配置：页数范围（可按需修改，如最大页数100）
const MIN_PAGE = 1;
const MAX_PAGE = 20;

// 获取下拉框DOM元素
const startPageSelect = document.getElementById('startPage');
const endPageSelect = document.getElementById('endPage');
const submitBtn = document.getElementById('submitBtn');
const BVNumSelect=document.getElementById('BVNum');
const resultArea = document.getElementById('resultArea');
const msgText = document.getElementById('msgText');
const downloadLink = document.getElementById('downloadLink');
// 初始化：动态生成开始页和结束页的下拉选项
function initPageOptions() {
    const option = document.createElement('option');
    // 生成开始页选项
    for (let i = MIN_PAGE; i <= MAX_PAGE; i++) {
        const option = document.createElement('option');
        option.value = i; // 选项值（数字类型，提交时自动转为字符串）
        option.textContent = `第 ${i} 页`; // 显示文本
        startPageSelect.appendChild(option);
    }

    // 生成结束页选项（和开始页一致，可单独配置）
    for (let i = MIN_PAGE; i <= MAX_PAGE; i++) {
        const option = document.createElement('option');
        option.value = i;
        option.textContent = `第 ${i} 页`;
        endPageSelect.appendChild(option);
    }

    // 默认选中：开始页1，结束页5（可选配置）
    startPageSelect.value = 1;
    endPageSelect.value = 5;
}

// 核心功能：提交时自动给结束页加1，并构造参数提交到后端
function submitPageParams() {
    // 1. 获取下拉框选中值（转为数字类型）
    const startPage = parseInt(startPageSelect.value);
    let endPage = parseInt(endPageSelect.value); // 原始结束页

    // 2. 核心：结束页自动加1（适配Python range左闭右开）
    const endPagePlus1 = endPage + 1;

    // 3. 校验参数（可选，避免无效值）
    if (startPage > endPage) {
        alert('错误：开始页不能大于结束页！');
        return;
    }
    if (startPage < MIN_PAGE || endPage > MAX_PAGE) {
        alert(`错误：页数需在 ${MIN_PAGE}-${MAX_PAGE} 之间！`);
        return;
    }
    BVNum=BVNumSelect.value.trim()
    // 禁用按钮，防止重复提交
    submitBtn.disabled = true;
    submitBtn.innerText = '提交中...';
    resultArea.style.display = 'none';
    downloadLink.style.display = 'none';


    // 4. 构造提交参数（前后端一致的参数名）
    const params = {
        bvNum:BVNum,
        startPage: startPage, // 开始页（原始值，无需修改）
        endPage: endPagePlus1, // 结束页+1，提交给后端
    };


    /***************** 方式2：POST请求（请求体传参，推荐传复杂数据） *****************/
    fetch('/getComment', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(params) // 转为JSON字符串
    })
    .then(response => response.json())
    .then(data => {
        console.log('后端响应：', data);
        alert(`提交成功！\n原始参数：BVNum开始页${BVNum}\n${startPage}，结束页${endPage}\n提交参数：开始页${startPage}，结束页${endPagePlus1}`);
        if(data.code==200) {
            // 入库成功：展示消息并显示下载按钮
            msgText.style.color = '#52c41a';
            msgText.innerText = `✅ ${data.msg}`;
            resultArea.style.display='block';
            // 设置下载链接（拼接BV号参数）
            downloadLink.style.display = 'block';
            downloadLink.href = `/api/bilibili/comment/downloadCommentCSV?bvNum=${BVNum}`;
        }else{
            msgText.style.color = '#ff4d4f';
            msgText.innerText   = `❌ ${data.message}`;
            resultArea.style.display='block';
            downloadLink.style.display = 'none';
        }
    })
    .catch(error => {
        console.error('提交失败：', error);
        alert('提交后端失败！');
        msgText.style.color = '#ff4d4f';
        msgText.innerText   = `❌ 请求失败:${error.message}`;
        downloadLink.style.display = 'none';
    })
    .finally(() => {          // 👈 链式调用，放在最后
        submitBtn.disabled = false;
        submitBtn.innerText = '提交到后端';
    });
}

// 可选：下载按钮点击时添加加载提示
downloadLink.addEventListener('click', (e) => {
    downloadLink.innerText = '生成CSV中...请稍候';
    downloadLink.style.pointerEvents = 'none';
    // 下载完成后恢复（可通过监听下载完成事件，简化版省略）
    setTimeout(() => {
        downloadLink.innerText = '📥 点击下载评论CSV文件';
        downloadLink.style.pointerEvents = 'auto';
    }, 2000);
});

// 页面加载完成后初始化下拉框
document.addEventListener('DOMContentLoaded', function() {
    initPageOptions();
    submitBtn.addEventListener('click', submitPageParams);
});