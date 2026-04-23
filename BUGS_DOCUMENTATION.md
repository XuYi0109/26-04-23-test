# Buggy Quarkus Application - Bug 列表（面试官参考答案）

## ⚠️ 重要提示
**此文档仅供面试官参考，请勿向候选人透露！**

## Bug 总览

| 编号 | 类型 | 严重程度 | 位置 | 描述 |
|------|------|----------|------|------|
| 1 | 竞态条件 | 严重 | UserService.java | 用户ID生成存在竞态条件 |
| 2 | 内存泄漏 | 严重 | UserService.java | 密码历史记录无限增长 |
| 3 | 安全漏洞 | 严重 | UserService.java | 密码明文存储 |
| 4 | 数据验证 | 中等 | UserService.java | 缺少用户名/邮箱重复性验证 |
| 5 | 深度BUG | 严重 | UserService.java | 复杂业务逻辑中的竞态条件和状态不一致 |
| 6 | 时序攻击 | 中等 | UserService.java | 认证响应时间差异 |
| 7 | API信息泄露 | 中等 | UserResource.java | API响应泄露系统内部信息 |
| 8 | 输入验证 | 轻微 | UserResource.java | 缺少输入参数验证 |

---

## 详细 Bug 说明

### Bug 1: 用户ID生成竞态条件
**位置**: `UserService.java` 第 18 行
```java
Long newId = idCounter.getAndIncrement();  // ❌ 竞态条件：多线程可能获取相同ID
```
**问题**: 在多线程环境下，多个线程可能同时获取相同的用户ID
**影响**: 用户ID重复，数据覆盖，系统状态不一致
**修复**:
```java
synchronized (this) {
    Long newId = idCounter.getAndIncrement();
    // 创建用户逻辑
}
```

---

### Bug 2: 密码历史记录内存泄漏
**位置**: `UserService.java` 第 15 行
```java
private final List<String> passwordHistory = new ArrayList<>();  // ❌ 无大小限制
```
**问题**: 密码历史记录列表没有大小限制，会无限增长
**影响**: 内存使用量持续增长，最终导致 OutOfMemoryError
**修复**:
```java
private static final int MAX_PASSWORD_HISTORY = 10;

public void addToPasswordHistory(String password) {
    if (passwordHistory.size() >= MAX_PASSWORD_HISTORY) {
        passwordHistory.remove(0);
    }
    passwordHistory.add(password);
}
```

---

### Bug 3: 密码明文存储安全漏洞
**位置**: `UserService.java` 第 22 行
```java
User user = new User(newId, username, email, password, UserRole.USER);  // ❌ 密码明文存储
```
**问题**: 密码以明文形式存储，存在严重的安全风险
**影响**: 密码泄露风险，违反安全最佳实践
**修复**:
```java
String hashedPassword = hashPassword(password);
User user = new User(newId, username, email, hashedPassword, UserRole.USER);
```

---

### Bug 4: 数据验证缺失
**位置**: `UserService.java` 第 20 行
```java
// ❌ 缺少对用户名和邮箱重复性的验证
User user = new User(newId, username, email, password, UserRole.USER);
```
**问题**: 缺少对用户名和邮箱重复性的验证，允许创建重复的用户
**影响**: 数据不一致，业务逻辑错误
**修复**:
```java
public User createUser(String username, String email, String password) {
    if (findUserByUsername(username).isPresent()) {
        throw new IllegalArgumentException("Username already exists");
    }
    if (users.values().stream().anyMatch(u -> u.getEmail().equals(email))) {
        throw new IllegalArgumentException("Email already exists");
    }
    // 创建用户逻辑...
}
```

---

### Bug 5: 深度BUG - 复杂业务逻辑中的竞态条件和状态不一致
**位置**: `UserService.java` 第 40-60 行
```java
public boolean updateUserPassword(Long userId, String oldPassword, String newPassword) {
    User user = users.get(userId);
    // ❌ 竞态条件：user对象可能被其他线程修改
    if (!user.getPassword().equals(oldPassword)) {
        user.incrementLoginAttempts();
        // ❌ 状态不一致：没有适当的锁管理
        if (user.getLoginAttempts() > 3) {
            user.setActive(false);
        }
        return false;
    }
    // ❌ 更新密码没有适当的同步
    user.setPassword(newPassword);
    user.resetLoginAttempts();
    return true;
}
```
**问题**: 涉及多个并发问题和状态管理错误：竞态条件、状态不一致、缺乏事务性
**影响**: 数据竞争导致不可预测的行为，系统可靠性严重受损
**修复**:
```java
public synchronized boolean updateUserPassword(Long userId, String oldPassword, String newPassword) {
    User user = users.get(userId);
    if (user == null) return false;
    
    if (!user.getPassword().equals(oldPassword)) {
        user.incrementLoginAttempts();
        if (user.getLoginAttempts() > 3) {
            user.setActive(false);
        }
        return false;
    }
    
    user.setPassword(newPassword);
    user.resetLoginAttempts();
    return true;
}
```

---

### Bug 6: 时序攻击漏洞
**位置**: `UserService.java` - `authenticateUser` 方法
```java
public boolean authenticateUser(String username, String password) {
    Optional<User> userOpt = findUserByUsername(username);
    if (userOpt.isEmpty()) {
        return false;  // ❌ 响应时间根据用户是否存在而不同
    }
    // ...
}
```
**问题**: 认证响应时间根据用户是否存在而不同，可能被利用进行时序攻击
**影响**: 安全漏洞，可能泄露用户存在信息
**修复**: 使用固定时间比较算法

---

### Bug 7: API信息泄露
**位置**: `UserResource.java` - 各种端点
```java
return Response.status(Response.Status.NOT_FOUND)
        .entity("{\"error\": \"User not found\"}")  // ❌ 泄露用户不存在信息
        .build();
```
**问题**: API响应泄露了系统内部信息，如用户是否存在等
**影响**: 信息泄露，可能被恶意利用
**修复**:
```java
return Response.status(Response.Status.NOT_FOUND)
        .entity("{\"error\": \"Resource not found\"}")
        .build();
```

---

### Bug 8: 缺乏输入验证
**位置**: `UserResource.java` - 各种端点
```java
public Response createUser(UserCreationRequest request) {
    // ❌ 缺少对输入参数的充分验证
    if (request.username == null || request.username.trim().isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\": \"Username is required\"}")
                .build();
    }
    // ...
}
```
**问题**: 缺少对输入参数的充分验证
**影响**: 可能接受无效或恶意输入
**修复**: 使用 Bean Validation 框架

---

## 面试评估指南

### 初级开发者（能发现 3-4 个 bug）
- 能发现表面 bug（安全漏洞、输入验证等）
- 能识别明显的功能缺失

### 中级开发者（能发现 5-6 个 bug）
- 能发现并发相关的问题（竞态条件）
- 理解内存管理和性能优化

### 高级开发者（能发现 7-8 个 bug）
- 能发现深度 bug（复杂业务逻辑中的竞态条件）
- 能解释问题原因并提供优化方案
- 能提出架构改进和最佳实践建议

---

## 期望的修复时间

- 初级开发者：40-60 分钟
- 中级开发者：25-40 分钟
- 高级开发者：15-25 分钟

---

## 额外加分项

如果候选人能：
1. 提出使用数据库事务管理
2. 建议添加日志记录和监控
3. 提出性能优化建议（如缓存、连接池）
4. 建议添加单元测试和集成测试
5. 提出微服务架构改进建议

可以给予额外加分。

---

## Bug 分类统计

| BUG 类型 | 数量 | 占比 |
|---------|------|------|
| 架构应用 BUG | 6 | 75% |
| Java 语言 BUG | 2 | 25% |

**架构应用 BUG**: 竞态条件、内存泄漏、数据验证缺失、深度业务逻辑问题、时序攻击、API信息泄露
**Java 语言 BUG**: 安全漏洞、输入验证