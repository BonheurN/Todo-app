-- Run this in psql or a DB tool if you want an explicit CREATE TABLE
CREATE TABLE IF NOT EXISTS tasks (
id serial PRIMARY KEY,
description text NOT NULL,
created_at timestamp with time zone DEFAULT now()
);
