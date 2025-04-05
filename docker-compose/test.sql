-- =============================
-- 建立 Schema 部分
-- =============================

-- 建立 user_info schema
CREATE SCHEMA IF NOT EXISTS user_info;

-- 建立 product_info schema
CREATE SCHEMA IF NOT EXISTS product_info;

-- =============================
-- user_info Schema 的表格與資料
-- =============================

-- 切換到 user_info schema
USE user_info;

-- 建立 users 表格
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login DATETIME NULL,
    is_active BOOLEAN DEFAULT TRUE
);

-- 插入 users 測試資料
INSERT INTO users (username, email, password_hash, full_name) VALUES
('john_doe', 'john.doe@example.com', 'hashed_password_1', '約翰・道'),
('mary_jane', 'mary.jane@example.com', 'hashed_password_2', '瑪麗・珍'),
('peter_chen', 'peter.chen@example.tw', 'hashed_password_3', '陳大明'),
('lisa_wang', 'lisa.wang@example.tw', 'hashed_password_4', '王小美'),
('david_liu', 'david.liu@example.com', 'hashed_password_5', '劉大衛');

-- 建立 contact 表格
CREATE TABLE IF NOT EXISTS contact (
    contact_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    contact_type ENUM('電話', '地址', '社群媒體') NOT NULL,
    contact_value VARCHAR(255) NOT NULL,
    is_primary BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- 插入 contact 測試資料
INSERT INTO contact (user_id, contact_type, contact_value, is_primary) VALUES
(1, '電話', '0912-345-678', TRUE),
(1, '地址', '臺北市信義區信義路五段7號', FALSE),
(2, '電話', '0923-456-789', TRUE),
(2, '社群媒體', 'facebook.com/mary_jane', FALSE),
(3, '電話', '0934-567-890', TRUE),
(3, '地址', '臺中市西屯區臺灣大道三段99號', FALSE),
(4, '電話', '0945-678-901', TRUE),
(5, '電話', '0956-789-012', TRUE),
(5, '地址', '高雄市前鎮區中山四路2號', FALSE);

-- =============================
-- product_info Schema 的表格與資料
-- =============================

-- 切換到 product_info schema
USE product_info;

-- 建立 product 表格
CREATE TABLE IF NOT EXISTS product (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    price DECIMAL(10, 2) NOT NULL,
    stock_quantity INT DEFAULT 0,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL
);

-- 插入 product 測試資料
INSERT INTO product (product_name, category, price, stock_quantity, description) VALUES
('筆記型電腦', '電子產品', 35000.00, 25, '高效能筆記型電腦，適合程式開發與遊戲'),
('智慧型手機', '電子產品', 12000.00, 50, '最新款智慧型手機，支援5G網路'),
('無線耳機', '電子配件', 2500.00, 100, '藍牙5.0無線耳機，降噪功能'),
('機械鍵盤', '電腦配件', 3200.00, 30, '機械軸體鍵盤，RGB背光'),
('顯示器', '電腦配件', 8500.00, 15, '27吋4K解析度顯示器，適合設計工作'),
('外接硬碟', '儲存裝置', 2000.00, 40, '1TB容量，USB 3.0傳輸速度'),
('電競滑鼠', '電腦配件', 1200.00, 45, '高DPI電競滑鼠，可自訂按鍵');

-- 執行一個簡單的查詢，確認資料已正確插入
SELECT 'user_info.users 資料筆數' AS info, COUNT(*) AS count FROM user_info.users
UNION ALL
SELECT 'user_info.contact 資料筆數', COUNT(*) FROM user_info.contact
UNION ALL
SELECT 'product_info.product 資料筆數', COUNT(*) FROM product_info.product;