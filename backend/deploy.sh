#!/bin/bash

# ========================================
# SkillMorph Lambda Deployment Script
# ========================================
# This script builds your FastAPI app as a Docker container
# and deploys it to AWS Lambda via ECR (Elastic Container Registry)
#
# Prerequisites:
# 1. AWS CLI installed and configured (aws configure)
# 2. Docker installed and running
# 3. ECR repository created: skillmorph-api
# 4. Lambda function created: skillmorph-api
#
# Usage: ./deploy.sh
# ========================================

set -e  # Exit on any error

# ========================================
# CONFIGURATION
# ========================================
# Update these values to match your AWS setup
AWS_REGION="ap-south-1"
ECR_REPOSITORY="skillmorph-api"
LAMBDA_FUNCTION="skillmorph-api"
IMAGE_TAG="latest"

# ========================================
# AUTO-DETECT AWS ACCOUNT ID
# ========================================
echo "🔍 Detecting AWS Account ID..."
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

if [ -z "$AWS_ACCOUNT_ID" ]; then
    echo "❌ Error: Could not detect AWS Account ID"
    echo "   Make sure AWS CLI is configured: aws configure"
    exit 1
fi

echo "✅ AWS Account ID: $AWS_ACCOUNT_ID"

# Full ECR repository URI
ECR_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY}"

# ========================================
# DEPLOYMENT START
# ========================================
echo ""
echo "🚀 Starting deployment to AWS Lambda..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📦 ECR Repository: ${ECR_URI}"
echo "⚡ Lambda Function: ${LAMBDA_FUNCTION}"
echo "🏷️  Image Tag: ${IMAGE_TAG}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ========================================
# STEP 1: LOGIN TO ECR
# ========================================
echo ""
echo "🔐 Step 1/5: Logging in to Amazon ECR..."
aws ecr get-login-password --region ${AWS_REGION} | \
    docker login --username AWS --password-stdin ${ECR_URI}

if [ $? -ne 0 ]; then
    echo "❌ Error: Failed to login to ECR"
    echo "   Make sure the ECR repository exists: ${ECR_REPOSITORY}"
    exit 1
fi

echo "✅ Successfully logged in to ECR"

# ========================================
# STEP 2: BUILD DOCKER IMAGE
# ========================================
echo ""
echo "🏗️  Step 2/5: Building Docker image..."
echo "   Using Dockerfile.lambda for AWS Lambda compatibility"

# Build for x86_64 (Intel/AMD) - most common
docker build --platform linux/amd64 -f Dockerfile.lambda -t ${ECR_REPOSITORY}:${IMAGE_TAG} .

# For ARM64 (Graviton2 - 20% cost savings), use this instead:
# docker build --platform linux/arm64 -f Dockerfile.lambda -t ${ECR_REPOSITORY}:${IMAGE_TAG} .

if [ $? -ne 0 ]; then
    echo "❌ Error: Docker build failed"
    exit 1
fi

echo "✅ Docker image built successfully"

# ========================================
# STEP 3: TAG IMAGE FOR ECR
# ========================================
echo ""
echo "🏷️  Step 3/5: Tagging image for ECR..."
docker tag ${ECR_REPOSITORY}:${IMAGE_TAG} ${ECR_URI}:${IMAGE_TAG}

echo "✅ Image tagged: ${ECR_URI}:${IMAGE_TAG}"

# ========================================
# STEP 4: PUSH TO ECR
# ========================================
echo ""
echo "📤 Step 4/5: Pushing image to Amazon ECR..."
echo "   This may take a few minutes depending on image size..."

docker push ${ECR_URI}:${IMAGE_TAG}

if [ $? -ne 0 ]; then
    echo "❌ Error: Failed to push image to ECR"
    exit 1
fi

echo "✅ Image pushed successfully to ECR"

# ========================================
# STEP 5: UPDATE LAMBDA FUNCTION
# ========================================
echo ""
echo "⚡ Step 5/5: Updating Lambda function..."

aws lambda update-function-code \
    --function-name ${LAMBDA_FUNCTION} \
    --image-uri ${ECR_URI}:${IMAGE_TAG} \
    --region ${AWS_REGION}

if [ $? -ne 0 ]; then
    echo "❌ Error: Failed to update Lambda function"
    echo "   Make sure the Lambda function exists: ${LAMBDA_FUNCTION}"
    exit 1
fi

# Wait for Lambda update to complete
echo ""
echo "⏳ Waiting for Lambda update to complete..."
aws lambda wait function-updated \
    --function-name ${LAMBDA_FUNCTION} \
    --region ${AWS_REGION}

# ========================================
# DEPLOYMENT COMPLETE
# ========================================
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Deployment complete!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Show function details
echo ""
echo "📊 Lambda Function Details:"
aws lambda get-function --function-name ${LAMBDA_FUNCTION} --region ${AWS_REGION} \
    --query 'Configuration.[FunctionName,LastModified,State,MemorySize,Timeout]' \
    --output table

echo ""
echo "🌐 Next Steps:"
echo "   1. Test your API endpoint (Function URL or API Gateway)"
echo "   2. Check CloudWatch Logs for any errors"
echo "   3. Update your Android app with the new endpoint URL"
echo ""
echo "📝 Useful Commands:"
echo "   View logs: aws logs tail /aws/lambda/${LAMBDA_FUNCTION} --follow"
echo "   Test function: aws lambda invoke --function-name ${LAMBDA_FUNCTION} response.json"
echo ""
