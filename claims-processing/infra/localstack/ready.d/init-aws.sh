#!/bin/bash
set -euo pipefail

awslocal sqs create-queue --queue-name claims-dlq
dlq_url="$(awslocal sqs get-queue-url --queue-name claims-dlq --query QueueUrl --output text)"
dlq_arn="$(awslocal sqs get-queue-attributes \
  --queue-url "$dlq_url" \
  --attribute-names QueueArn \
  --query Attributes.QueueArn \
  --output text)"

queue_attributes="$(cat <<EOF
{
  "VisibilityTimeout": "30",
  "RedrivePolicy": "{\"deadLetterTargetArn\":\"${dlq_arn}\",\"maxReceiveCount\":\"4\"}"
}
EOF
)"

awslocal sqs create-queue --queue-name claims-death --attributes "$queue_attributes"
awslocal sqs create-queue --queue-name claims-standard --attributes "$queue_attributes"
awslocal dynamodb create-table \
  --table-name claims \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

echo "claims-localstack-init-complete"
