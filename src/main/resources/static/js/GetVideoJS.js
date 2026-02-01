$(function() {
    // 提交按钮点击事件
    $("#submitBtn").click(function() {
        // 1. 获取并解析BV号（支持换行、逗号、空格分隔）
        let bvInput = $("#BVNum").val().trim();
        if (!bvInput) {
            $("#statusContent").html(`
                <div class="status-error">
                    ❌ BV号不能为空！
                </div>
            `);
            return;
        }

        // 解析成数组：替换换行、逗号、空格为分隔符
        let bvNums = bvInput.split(/[\n,，\s]+/).filter(bv => bv.trim() !== "");

        // 2. 显示加载状态
        $("#submitBtn").text("下载文件中...").prop("disabled", true);
        $("#statusContent").html(`
            <div class="status-loading">
                <div class="spinner"></div>
                <div>
                    <p>📥 正在处理 ${bvNums.length} 个视频...</p>
                    <p>请稍候，下载过程可能需要几分钟时间</p>
                </div>
            </div>
        `);

        // 3. 构造请求参数
        let requestData = {
            bvNums: bvNums,
            savePath: "" // 使用后端默认路径，也可自定义
        };

        // 4. 发送请求
        $.ajax({
            url: "/api/bilibili/video/getvideo",
            type: "POST",
            contentType: "application/json",
            data: JSON.stringify(requestData),
            success: function(res) {
                // 5. 恢复按钮状态
                $("#submitBtn").text("提交到后端").prop("disabled", false);
                
                // 6. 展示结果
                if (res.code === 200) {
                    let successHtml = `
                        <div class="status-success">
                            <h4>🎉 处理完成！</h4>
                            <div class="result-summary">
                                <p>状态码：${res.code}</p>
                                <p>提示：${res.msg}</p>
                                <p>成功数：${res.successCount || 0} | 失败数：${res.failCount || 0}</p>
                            </div>
                    `;
                    
                    if (res.detail && res.detail.length > 0) {
                        successHtml += `<div class="result-detail">
                            <h5>详细结果：</h5>
                            <ul>`;
                        
                        res.detail.forEach(item => {
                            let className = item.code === 200 ? "result-success" : "result-error";
                            let icon = item.code === 200 ? "✅" : "❌";
                            successHtml += `<li class="${className}">
                                ${icon} BV号：${item.bvNum} | 状态：${item.msg}
                            </li>`;
                        });
                        
                        successHtml += `</ul></div>`;
                    }
                    
                    successHtml += `</div>`;
                    $("#statusContent").html(successHtml);
                } else {
                    $("#statusContent").html(`
                        <div class="status-error">
                            <h4>❌ 处理失败</h4>
                            <p>状态码：${res.code}</p>
                            <p>提示：${res.msg}</p>
                        </div>
                    `);
                }
            },
            error: function(xhr, status, error) {
                // 恢复按钮状态
                $("#submitBtn").text("提交到后端").prop("disabled", false);
                
                // 展示错误信息
                $("#statusContent").html(`
                    <div class="status-error">
                        <h4>❌ 请求失败</h4>
                        <p>状态：${status}</p>
                        <p>错误：${error}</p>
                        <p>请检查网络连接或稍后重试</p>
                    </div>
                `);
            }
        });
    });

    // 重置按钮点击事件
    $("#resetBtn").click(function() {
        $("#BVNum").val("");
        $("#statusContent").html(`
            <div class="status-initial">
                📥 请输入BV号并点击"提交到后端"按钮开始下载
            </div>
        `);
    });

    // 清空数据库按钮事件
    $("#clearBtn").click(function() {
        if (!confirm("确定要清空视频数据库和所有下载文件吗？此操作不可恢复！")) {
            return;
        }

        // 显示加载状态
        $("#statusContent").html(`
            <div class="status-loading">
                <div class="spinner"></div>
                <div>
                    <p>🗑️ 正在清空数据库...</p>
                    <p>请稍候，此操作可能需要几秒钟时间</p>
                </div>
            </div>
        `);

        $.ajax({
            url: "/api/bilibili/video/clear",
            type: "POST",
            contentType: "application/json",
            success: function(res) {
                if (res.code === 200) {
                    $("#statusContent").html(`
                        <div class="status-success">
                            <h4>🎉 清空成功！</h4>
                            <p>提示：${res.msg}</p>
                            <p>视频数据库和所有下载文件已清空</p>
                        </div>
                    `);
                } else {
                    $("#statusContent").html(`
                        <div class="status-error">
                            <h4>❌ 清空失败</h4>
                            <p>状态码：${res.code}</p>
                            <p>提示：${res.msg}</p>
                        </div>
                    `);
                }
            },
            error: function(xhr, status, error) {
                $("#statusContent").html(`
                    <div class="status-error">
                        <h4>❌ 请求失败</h4>
                        <p>状态：${status}</p>
                        <p>错误：${error}</p>
                        <p>请检查网络连接或稍后重试</p>
                    </div>
                `);
            }
        });
    });
});




// $(document).on('click', '#submitBtn', function(){
//
//         let bvNumElement = document.getElementById('BVNum');
//         let bvNum = bvNumElement.value.trim();
//
//         if (!bvNum) {
//             alert("请输入BV号");
//             return;
//         }
//
//         $.ajax({
//             url:"/api/bilibili/video/getvideo",
//             type:"POST",
//             contentType:"application/json",
//             data:JSON.stringify({
//                 bvNum: bvNum
//             }),
//             //请求成功回传
//             success:function(res){
//                 console.log("请求成功，后端返回：", res);
//                 if (res.code === 200) {
//                     alert("✅ 视频下载成功！\n" + res.data);
//                 } else {
//                     alert("❌ 下载失败：" + res.msg);
//                 }
//             },
//             // 请求失败的回调（网络错误/后端报错）
//             error: function(xhr, status, error) {
//             console.error("请求失败：", status, error);
//             alert("❌ 请求失败：" + xhr.status);
//             },
//         });
//
//     })
//
//     // 清空视频数据库按钮点击事件
//     $(document).on('click', '#clearBtn', function(){
//         if (!confirm("确定要清空视频数据库吗？此操作将删除所有视频数据和文件，不可恢复！")) {
//             return;
//         }
//
//         $.ajax({
//             url:"/api/bilibili/video/clear",
//             type:"POST",
//             contentType:"application/json",
//             data:JSON.stringify({}),
//             //请求成功回传
//             success:function(res){
//                 console.log("请求成功，后端返回：", res);
//                 if (res.code === 200) {
//                     alert("✅ 视频数据库清空成功！");
//                 } else {
//                     alert("❌ 清空失败：" + res.msg);
//                 }
//             },
//             // 请求失败的回调（网络错误/后端报错）
//             error: function(xhr, status, error) {
//             console.error("请求失败：", status, error);
//             alert("❌ 请求失败：" + xhr.status);
//             },
//         });
//     })
