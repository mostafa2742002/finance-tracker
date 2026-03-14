CREATE Table transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount DECIMAL(10, 2) NOT NULL,
    description VARCHAR(255),
    category VARCHAR(100),
    type VARCHAR(10) NOT NULL check (type IN ('INCOME', 'EXPENSE')),
    date TIMESTAMP NOT NULL,
    is_fraud BOOLEAN DEFAULT FALSE,
    fraud_score DECIMAL(3,2),
    fraud_reason VARCHAR(255),
    ai_category VARCHAR(100),
    ai_adivce VARCHAR(255),
    ai_proccessed BOOLEAN DEFAULT FALSE,
    client_id VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
    

    CREATE INDEX idx_transactions_user_id ON transactions(user_id);
    CREATE INDEX idx_transactions_date ON transactions(date);
    CREATE INDEX idx_transactions_user_category ON transactions(user_id, category);

)