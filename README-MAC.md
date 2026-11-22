##Terminal 1 – SwiftDrop

cd "/path/to/SwiftDrop"

docker compose up -d rabbitmq

source stylecart/backend/target/local-tls/swiftdrop-env.sh

PATH="/opt/homebrew/bin:$PATH" DB_PROFILE=rds infrastructure/scripts/run-mac-all.sh


##Terminal 2 – StyleCart Backend

cd "/path/to/SwiftDrop/stylecart/backend"

set -a
source ../.env
set +a

bash mvnw spring-boot:run


##Terminal 3 – StyleCart Frontend

cd "/path/to/SwiftDrop/stylecart/frontend"

npm run dev

