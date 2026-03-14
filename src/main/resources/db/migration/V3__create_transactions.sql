CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount DECIMAL(15,2) NOT NULL,
    description VARCHAR(500),
    category VARCHAR(100),
    type VARCHAR(10) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    date TIMESTAMP NOT NULL,
    is_fraud BOOLEAN DEFAULT FALSE,
    fraud_score DECIMAL(3,2),
    fraud_reason VARCHAR(300),
    ai_category VARCHAR(100),
    ai_advice TEXT,
    ai_processed BOOLEAN DEFAULT FALSE,
    client_id UUID UNIQUE,               
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_date ON transactions(date);
CREATE INDEX idx_transactions_user_category ON transactions(user_id, category);
