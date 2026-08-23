# MedOS Supabase Option C — DB + Auth Migration

## Environment Variables (.env)
SUPABASE_URL=https://rgqsehyqwbermmqkialm.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5c...
DB_URL=postgresql://postgres:[DB_PASSWORD]@db.your-project.supabase.co:5432/postgres
REDIS_URL=redis://localhost:6379

## Architecture Changes
- DB: Supabase PostgreSQL (replaces local db container)
- Auth: Supabase Auth (replaces custom JWT / BCrypt)
- Cache: Keep Redis (optional: switch to Supabase Redis if available)
- Flyway: Apply V1-V3 to Supabase DB via `mvn flyway:migrate`

## Auth Migration Path (Recommended Hybrid)
1. Keep JWT token generation for API statelessness
2. Replace `UserRepository` with Supabase Auth user lookup (via service role)
3. Replace `PasswordEncoder` with Supabase Auth sign-in verification
4. Update `JwtTokenProvider` to read user metadata from Supabase `auth.users`

## Sync / Deploy Script
```bash
#!/bin/bash
# Apply migrations to Supabase DB
mvn flyway:migrate -Dflyway.url="$DB_URL" -Dflyway.user=postgres -Dflyway.password="$DB_PASSWORD"

# Verify auth connection
curl -H "apikey: $SUPABASE_SERVICE_ROLE_KEY" "$SUPABASE_URL/auth/v1/admin/users"

# Build and deploy backend (points to Supabase)
docker compose --env-file .env.supabase up -d --build
```

## Security Notes
- Never expose SERVICE_ROLE_KEY in frontend
- Use ANON_KEY only for public endpoints; service role only in backend
- Enable RLS (Row Level Security) on Supabase tables if accessing directly
- Rotate keys after first deploy
