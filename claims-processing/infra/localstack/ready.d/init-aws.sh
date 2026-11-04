#!/bin/bash
set -euo pipefail

awslocal sqs create-queue --queue-name claims-death --attributes VisibilityTimeout=30
awslocal sqs create-queue --queue-name claims-standard --attributes VisibilityTimeout=30
awslocal dynamodb create-table \
  --table-name claims \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

echo "claims-localstack-init-complete"
