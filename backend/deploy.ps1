# SkillMorph Lambda Deployment Script (PowerShell)
$ErrorActionPreference = "Stop"

# Configuration
$AWS_REGION = "ap-south-1"
$ECR_REPOSITORY = "skillmorph-api"
$LAMBDA_FUNCTION = "skillmorph-api"
$IMAGE_TAG = "latest"

Write-Host ""
Write-Host "Detecting AWS Account ID..." -ForegroundColor Cyan
$AWS_ACCOUNT_ID = (aws sts get-caller-identity --query Account --output text)

if (-not $AWS_ACCOUNT_ID) {
    Write-Host "Error: Could not detect AWS Account ID" -ForegroundColor Red
    exit 1
}

Write-Host "AWS Account ID: $AWS_ACCOUNT_ID" -ForegroundColor Green

$ECR_URI = "$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPOSITORY"

Write-Host ""
Write-Host "Starting deployment to AWS Lambda..." -ForegroundColor Cyan
Write-Host "ECR Repository: $ECR_URI"
Write-Host "Lambda Function: $LAMBDA_FUNCTION"
Write-Host "Image Tag: $IMAGE_TAG"

# Step 1: Login to ECR
Write-Host ""
Write-Host "Step 1/5: Logging in to Amazon ECR..." -ForegroundColor Cyan
$password = aws ecr get-login-password --region $AWS_REGION

if (-not $password) {
    Write-Host "Error: Failed to get ECR login password" -ForegroundColor Red
    exit 1
}

$password | docker login --username AWS --password-stdin $ECR_URI

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to login to ECR" -ForegroundColor Red
    exit 1
}

Write-Host "Successfully logged in to ECR" -ForegroundColor Green

# Step 2: Build Docker image
Write-Host ""
Write-Host "Step 2/5: Building Docker image..." -ForegroundColor Cyan

docker build -f Dockerfile.lambda -t "${ECR_REPOSITORY}:${IMAGE_TAG}" .

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Docker build failed" -ForegroundColor Red
    exit 1
}

Write-Host "Docker image built successfully" -ForegroundColor Green

# Step 3: Tag image for ECR
Write-Host ""
Write-Host "Step 3/5: Tagging image for ECR..." -ForegroundColor Cyan
docker tag "${ECR_REPOSITORY}:${IMAGE_TAG}" "${ECR_URI}:${IMAGE_TAG}"

Write-Host "Image tagged successfully" -ForegroundColor Green

# Step 4: Push to ECR
Write-Host ""
Write-Host "Step 4/5: Pushing image to Amazon ECR..." -ForegroundColor Cyan
Write-Host "This may take a few minutes..."

docker push "${ECR_URI}:${IMAGE_TAG}" --platform linux/amd64

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: Failed to push image to ECR" -ForegroundColor Red
    exit 1
}

Write-Host "Image pushed successfully to ECR" -ForegroundColor Green

# Step 5: Update Lambda function
Write-Host ""
Write-Host "Step 5/5: Updating Lambda function..." -ForegroundColor Cyan

aws lambda update-function-code --function-name $LAMBDA_FUNCTION --image-uri "${ECR_URI}:${IMAGE_TAG}" --region $AWS_REGION

if ($LASTEXITCODE -ne 0) {
    Write-Host "Warning: Lambda function does not exist yet" -ForegroundColor Yellow
    Write-Host "Create the Lambda function in AWS Console using this image:" -ForegroundColor Yellow
    Write-Host "${ECR_URI}:${IMAGE_TAG}" -ForegroundColor White
} else {
    Write-Host "Lambda function updated successfully" -ForegroundColor Green
}

Write-Host ""
Write-Host "Deployment complete!" -ForegroundColor Green
Write-Host ""
