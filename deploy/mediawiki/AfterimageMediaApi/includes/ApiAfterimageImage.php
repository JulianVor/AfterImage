<?php

use MediaWiki\MediaWikiServices;
use Wikimedia\ParamValidator\ParamValidator;
use Wikimedia\ParamValidator\TypeDef\IntegerDef;

/**
 * Authenticated, read-only transport for a bounded raster thumbnail.
 *
 * Private file URLs are served outside the Action API and therefore cannot be
 * opened with BotPassword credentials. This module keeps the transfer inside
 * the authenticated API session without exposing arbitrary filesystem paths.
 */
class ApiAfterimageImage extends ApiBase {
	private const MAX_WIDTH = 2000;
	private const MAX_BYTES = 25 * 1024 * 1024;

	public function execute() {
		if ( !$this->getUser()->isRegistered() ) {
			$this->dieWithError( 'afterimagemediaapi-login-required', 'notloggedin' );
		}
		$params = $this->extractRequestParams();
		$title = MediaWikiServices::getInstance()
			->getTitleFactory()
			->newFromText( $params['title'], NS_FILE );

		if ( !$title || $title->getNamespace() !== NS_FILE ) {
			$this->dieWithError( 'afterimagemediaapi-invalid-title', 'invalidtitle' );
		}
		$this->checkTitleUserPermissions( $title, 'read' );

		$file = MediaWikiServices::getInstance()->getRepoGroup()->findFile( $title );
		if ( !$file || !$file->exists() ) {
			$this->dieWithError( 'afterimagemediaapi-file-missing', 'filenotfound' );
		}

		$mime = $file->getMimeType();
		if ( strpos( $mime, 'image/' ) !== 0 || strtolower( $mime ) === 'image/svg+xml' ) {
			$this->dieWithError( 'afterimagemediaapi-unsupported', 'unsupportedimage' );
		}

		$requestedWidth = min( (int)$params['width'], self::MAX_WIDTH );
		$sourceWidth = (int)$file->getWidth();
		$width = $sourceWidth > 0 ? min( $requestedWidth, $sourceWidth ) : $requestedWidth;
		$thumbnail = $file->transform( [ 'width' => $width ] );
		if ( !$thumbnail || $thumbnail->isError() || !$thumbnail->hasFile() ) {
			$this->dieWithError( 'afterimagemediaapi-transform-failed', 'transformfailed' );
		}

		$path = $thumbnail->getLocalCopyPath();
		$content = $path ? file_get_contents( $path ) : false;
		if ( $content === false ) {
			$this->dieWithError( 'afterimagemediaapi-read-failed', 'imagereadfailed' );
		}
		if ( strlen( $content ) > self::MAX_BYTES ) {
			$this->dieWithError( 'afterimagemediaapi-too-large', 'imagetoolarge' );
		}

		$this->getResult()->addValue( null, 'afterimageimage', [
			'filename' => $file->getName(),
			'mime' => $mime,
			'width' => $thumbnail->getWidth(),
			'height' => $thumbnail->getHeight(),
			'content' => base64_encode( $content ),
		] );
	}

	public function getAllowedParams() {
		return [
			'title' => [
				ParamValidator::PARAM_TYPE => 'string',
				ParamValidator::PARAM_REQUIRED => true,
			],
			'width' => [
				ParamValidator::PARAM_TYPE => 'integer',
				ParamValidator::PARAM_DEFAULT => self::MAX_WIDTH,
				IntegerDef::PARAM_MIN => 320,
				IntegerDef::PARAM_MAX => self::MAX_WIDTH,
			],
		];
	}

	public function isWriteMode() {
		return false;
	}
}
