<template>
  <section class="page-heading">
    <div>
      <h1>网站介绍</h1>
      <p>图书管理系统为读者提供馆藏查询、借阅申请和归还登记服务。</p>
    </div>
  </section>
  <section class="panel prose-panel" aria-labelledby="workflow">
    <h2 id="workflow">借阅流程</h2>
    <ol class="workflow">
      <li><strong>查找图书</strong><p>在书籍管理页面查看图书信息和剩余可借数量。</p></li>
      <li><strong>提交申请</strong><p>登录读者账号，对有可借库存的图书提交申请。</p></li>
      <li><strong>查看审批结果</strong><p>管理员处理申请后，可在个人中心的“我的借阅”查看结果。批准后显示“已借出”。</p></li>
      <li><strong>确认归还</strong><p>交还实体图书后，在书籍管理或个人中心点击“归还图书”，系统会更新记录和库存。</p></li>
    </ol>
    <RouterLink class="primary-link" to="/books">浏览馆藏</RouterLink>
  </section>
</template>
