ARG NGINX_IMAGE
FROM ${NGINX_IMAGE}
COPY dist/ /usr/share/nginx/html/
